package com.ondo.retail.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 세션 쿠키를 진짜 톰캣에서 확인한다.
 *
 * <p>MockMvc 로는 못 본다 — {@code server.servlet.session.cookie.*} 가 서블릿 컨테이너를
 * 거쳐 적용되는 설정이라, 컨테이너 없이 도는 MockMvc 에서는 기본값(SESSION)이 나온다.
 *
 * <p>쿠키 이름이 중요한 이유는 배포하면 도매와 API 도메인을 같이 쓰기 때문이다.
 * 이름이 같으면 나중에 로그인한 쪽이 앞의 것을 덮어쓴다.
 *
 * <p>자바 21 내장 HttpClient 를 쓴다. TestRestTemplate 은 의존성이 더 필요해서 피했다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSessionTest {

    private static final String LOGIN_BODY = """
            {"email":"bombom@ondo.test","password":"ondo1234!"}""";

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    @DisplayName("로그인 → 내 정보 → 로그아웃까지 세션이 이어진다")
    void 세션_흐름() throws Exception {
        HttpResponse<String> 로그인 = post("/api/retail/auth/login", LOGIN_BODY, null);
        assertThat(로그인.statusCode()).isEqualTo(200);

        String 쿠키 = 로그인.headers().firstValue("set-cookie").orElseThrow();
        assertThat(쿠키)
                .as("도매와 API 도메인을 같이 써서 이름이 겹치면 서로 덮어쓴다")
                .startsWith("SESSION_RETAIL=")
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        String 세션 = 쿠키.split(";", 2)[0];

        HttpResponse<String> 내정보 = get("/api/retail/auth/me", 세션);
        assertThat(내정보.statusCode()).isEqualTo(200);
        assertThat(내정보.body()).contains("bombom@ondo.test");

        assertThat(post("/api/retail/auth/logout", "", 세션).statusCode())
                .as("로그아웃은 본문 없이 204 다")
                .isEqualTo(204);
        assertThat(get("/api/retail/auth/me", 세션).statusCode())
                .as("로그아웃한 쿠키는 더 안 통한다")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("세션을 들고 로그인해도 새 세션을 준다 — 세션 고정 방어 (MUL-119)")
    void 로그인하면_세션_id_가_바뀐다() throws Exception {
        String 먼저_받은_세션 = 세션(post("/api/retail/auth/login", LOGIN_BODY, null));

        // 공격자가 알고 있는 세션 id 를 피해자가 들고 로그인하는 상황
        HttpResponse<String> 다시_로그인 = post("/api/retail/auth/login", LOGIN_BODY, 먼저_받은_세션);
        assertThat(다시_로그인.statusCode()).isEqualTo(200);
        assertThat(다시_로그인.headers().firstValue("set-cookie"))
                .as("들고 온 세션을 그대로 쓰면 새 쿠키가 안 나간다 — 그게 이 버그였다")
                .isPresent();

        String 새_세션 = 세션(다시_로그인);
        assertThat(새_세션).isNotEqualTo(먼저_받은_세션);
        assertThat(get("/api/retail/auth/me", 새_세션).statusCode()).isEqualTo(200);
        assertThat(get("/api/retail/auth/me", 먼저_받은_세션).statusCode())
                .as("옛 세션 id 로는 더 안 통해야 공격자가 미리 심은 id 가 쓸모없어진다")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("로그인에 실패하면 세션을 주지 않는다")
    void 실패하면_쿠키가_없다() throws Exception {
        HttpResponse<String> 응답 = post("/api/retail/auth/login", """
                {"email":"bombom@ondo.test","password":"틀린비번"}""", null);

        assertThat(응답.statusCode()).isEqualTo(401);
        assertThat(응답.headers().firstValue("set-cookie")).isEmpty();
    }

    /** set-cookie 에서 "이름=값" 만 떼어낸다. */
    private static String 세션(HttpResponse<String> 응답) {
        return 응답.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
