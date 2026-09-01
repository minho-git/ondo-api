package com.ondo.retail.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 승인 게이트. 승인된 계정만 일반 API 를 쓸 수 있다.
 *
 * <p>아직 막을 소매 API 가 인증밖에 없어서 테스트용 엔드포인트를 하나 띄워서 확인한다.
 * 상품 API 가 생기면 그게 같은 규칙에 자동으로 걸린다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApprovalGateTest.TestControllerConfig.class)
class ApprovalGateTest {

    private static final String PROTECTED = "/api/retail/__gate-test";

    /** 게이트가 막을 대상. @Import 로만 올려서 다른 테스트에는 안 붙는다. */
    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        ProtectedController protectedController() {
            return new ProtectedController();
        }
    }

    @RestController
    static class ProtectedController {
        @GetMapping(PROTECTED)
        String ok() {
            return "ok";
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    @DisplayName("승인된 계정은 통과한다")
    void 승인됨() throws Exception {
        String 쿠키 = login("bombom@ondo.test");
        assertThat(get(PROTECTED, 쿠키).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("승인 안 된 계정은 403 이다")
    void 승인_안됨() throws Exception {
        String 쿠키 = login("pending@ondo.test");

        HttpResponse<String> 응답 = get(PROTECTED, 쿠키);
        assertThat(응답.statusCode()).isEqualTo(403);
        assertThat(응답.body()).contains("ACCOUNT_NOT_APPROVED");
    }

    @Test
    @DisplayName("승인 안 돼도 내 정보와 로그아웃은 열려 있다 — 승인 대기 화면을 봐야 한다")
    void 승인_안됨_예외경로() throws Exception {
        String 쿠키 = login("pending@ondo.test");

        assertThat(get("/api/retail/auth/me", 쿠키).statusCode()).isEqualTo(200);
        assertThat(post("/api/retail/auth/logout", 쿠키).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그인 안 하면 403 이 아니라 401 이다")
    void 로그인_안함() throws Exception {
        HttpResponse<String> 응답 = get(PROTECTED, null);
        assertThat(응답.statusCode()).isEqualTo(401);
        assertThat(응답.body()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("문서는 로그인 없이 열린다")
    void 문서는_공개() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isEqualTo(200);
    }

    private String login(String email) throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/retail/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"ondo1234!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return r.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
