package com.ondo.retail.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * CORS 계약 (MUL-85). 소매 화면이 다른 주소에서 이 API 를 부를 수 있는지 본다.
 *
 * <p>진짜 서버를 띄우고 실제 HTTP 로 확인한다. CORS 는 필터 순서에 걸리는 문제라
 * (시큐리티가 preflight 를 먼저 막으면 끝이다) 슬라이스보다 이쪽이 확실하다.
 *
 * <p>허용 목록을 테스트가 직접 정한다 — application-local.yml 값이 바뀌어도 이 계약은 그대로다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ondo.cors.allowed-origins=http://localhost:3001")
class CorsTest {

    private static final String ALLOWED = "http://localhost:3001";
    private static final String PUBLIC_API = "/api/retail/auth/email-availability?email=x@ondo.test";

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    @DisplayName("허용 origin 의 preflight 는 로그인 없이 통과한다")
    void preflight_통과() throws Exception {
        HttpResponse<String> 응답 = preflight(PUBLIC_API, ALLOWED, "GET");

        assertThat(응답.statusCode()).isEqualTo(200);
        assertThat(응답.headers().firstValue("access-control-allow-origin")).hasValue(ALLOWED);
        assertThat(응답.headers().firstValue("access-control-allow-credentials")).hasValue("true");
    }

    @Test
    @DisplayName("보호된 경로의 preflight 도 401 이 아니다 — 이게 안 되면 본 요청이 시작도 못 한다")
    void 보호된_경로_preflight() throws Exception {
        HttpResponse<String> 응답 = preflight("/api/retail/cart", ALLOWED, "GET");

        assertThat(응답.statusCode()).isEqualTo(200);
        assertThat(응답.headers().firstValue("access-control-allow-origin")).hasValue(ALLOWED);
    }

    @Test
    @DisplayName("허용 origin 의 실요청 응답에 CORS 헤더가 붙는다")
    void 실요청_헤더() throws Exception {
        HttpResponse<String> 응답 = client.send(
                HttpRequest.newBuilder(URI.create(base() + PUBLIC_API))
                        .header("Origin", ALLOWED)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(응답.statusCode()).isEqualTo(200);
        assertThat(응답.headers().firstValue("access-control-allow-origin")).hasValue(ALLOWED);
        assertThat(응답.headers().firstValue("access-control-allow-credentials")).hasValue("true");
    }

    @Test
    @DisplayName("허용 목록 밖 origin 은 차단된다")
    void 허용목록_밖() throws Exception {
        HttpResponse<String> 응답 = preflight(PUBLIC_API, "https://evil.example", "GET");

        assertThat(응답.statusCode()).isEqualTo(403);
        assertThat(응답.headers().firstValue("access-control-allow-origin")).isEmpty();
    }

    /** 브라우저가 본 요청 전에 보내는 "가도 되냐" 질문. 로그인 정보가 없다. */
    private HttpResponse<String> preflight(String path, String origin, String method) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "Content-Type")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
