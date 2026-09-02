package com.ondo.wholesale.auth;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 쿠키를 진짜 톰캣에서 확인한다 (MUL-69).
 *
 * <p>MockMvc 로는 못 보는 것들이 있다 — 받은 쿠키로 <b>다음 요청이 실제로 인증되는지</b>,
 * 로그아웃한 쿠키가 정말 죽는지, 로그인할 때 세션 id 가 갈리는지. 서블릿 컨테이너를
 * 실제로 태워야 확인된다.
 *
 * <p>자바 21 내장 {@link HttpClient} 를 쓴다. 쿠키를 자동으로 물고 가지 않도록
 * {@code Cookie} 헤더를 매번 직접 넣는다 — 어느 쿠키로 부르는지가 이 테스트의 요점이다.
 *
 * <p>보호 API 는 테스트 소스의 {@code ProtectedTestController} 를 빌려 쓴다.
 * MUL-70 이 실제 {@code GET /me} 를 만들면 그쪽으로 옮기면 된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSessionTest extends PostgresTestSupport {

    private static final String 비밀번호 = "Abcd1234!";
    private static final String 로그인_본문 = """
            {"email":"approved@ondo.test","password":"Abcd1234!"}""";

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void 도매처를_넣는다() {
        jdbc.update("""
                        insert into wholesale.wholesaler
                            (email, password_hash, biz_reg_no, biz_name, biz_owner_name, approval_status)
                        values (?, ?, ?, ?, ?, ?)""",
                "approved@ondo.test", passwordEncoder.encode(비밀번호),
                "9999999999", "온도상사", "김대표", "APPROVED");
    }

    @AfterEach
    void 지운다() {
        jdbc.execute("delete from wholesale.spring_session");
        jdbc.execute("delete from wholesale.wholesaler");
    }

    /**
     * <b>AC 1</b> — 로그인하면 세션 쿠키가 붙는다.
     *
     * <p>쿠키 이름을 확인하는 이유는 배포하면 도매·소매가 API 도메인을 같이 쓰기 때문이다.
     * 이름이 같으면 나중에 로그인한 쪽이 앞의 것을 덮어써서, 한쪽이 저절로 로그아웃된다.
     */
    @Test
    void 로그인하면_세션쿠키를_내려준다() throws Exception {
        HttpResponse<String> 응답 = post("/api/wholesale/auth/login", 로그인_본문, null);

        assertThat(응답.statusCode()).isEqualTo(200);
        assertThat(응답.headers().firstValue("set-cookie").orElseThrow())
                .as("소매가 SESSION_RETAIL 을 쓴다. 이름이 겹치면 서로 덮어쓴다")
                .startsWith("SESSION_WHOLESALE=")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void 로그인에_실패하면_쿠키를_주지_않는다() throws Exception {
        HttpResponse<String> 응답 = post("/api/wholesale/auth/login", """
                {"email":"approved@ondo.test","password":"틀린비밀번호!"}""", null);

        assertThat(응답.statusCode()).isEqualTo(401);
        assertThat(응답.headers().firstValue("set-cookie")).isEmpty();
    }

    /**
     * 로그인할 때마다 세션 id 가 새로 나온다 (세션 고정 방어).
     *
     * <p>공격자가 미리 만들어 피해자에게 쥐여준 세션 id 가 로그인 뒤에도 살아 있으면
     * 그 id 로 남의 계정에 들어올 수 있다. 이미 들고 있는 쿠키로 다시 로그인해도
     * 서버가 <b>다른 id</b> 를 줘야 한다.
     */
    @Test
    void 이미_가진_쿠키로_다시_로그인해도_세션id가_바뀐다() throws Exception {
        String 첫_쿠키 = 세션쿠키(post("/api/wholesale/auth/login", 로그인_본문, null));

        String 둘째_쿠키 = 세션쿠키(post("/api/wholesale/auth/login", 로그인_본문, 첫_쿠키));

        assertThat(둘째_쿠키).isNotEqualTo(첫_쿠키);
        assertThat(get("/test/protected", 첫_쿠키).statusCode())
                .as("옛 쿠키는 죽어야 한다 — 안 죽으면 세션 고정 방어가 뚫린다")
                .isEqualTo(401);
    }

    /** <b>AC 3</b> — 로그아웃한 쿠키로 보호 API 를 부르면 401 이다. */
    @Test
    void 로그아웃하면_같은_쿠키로_보호API를_부를_수_없다() throws Exception {
        String 쿠키 = 세션쿠키(post("/api/wholesale/auth/login", 로그인_본문, null));

        assertThat(get("/test/protected", 쿠키).statusCode())
                .as("로그아웃 전에는 통과해야 비교가 성립한다")
                .isEqualTo(200);

        assertThat(post("/api/wholesale/auth/logout", "", 쿠키).statusCode()).isEqualTo(204);

        assertThat(get("/test/protected", 쿠키).statusCode()).isEqualTo(401);
    }

    /** 세션은 DB 에 있다. 로그아웃하면 행도 같이 사라진다. */
    @Test
    void 로그아웃하면_세션_행이_사라진다() throws Exception {
        String 쿠키 = 세션쿠키(post("/api/wholesale/auth/login", 로그인_본문, null));
        assertThat(세션_행수()).isEqualTo(1);

        post("/api/wholesale/auth/logout", "", 쿠키);

        assertThat(세션_행수()).isZero();
    }

    // ── 도구 ────────────────────────────────

    private Integer 세션_행수() {
        return jdbc.queryForObject("select count(*) from wholesale.spring_session", Integer.class);
    }

    private String 세션쿠키(HttpResponse<String> 응답) {
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
