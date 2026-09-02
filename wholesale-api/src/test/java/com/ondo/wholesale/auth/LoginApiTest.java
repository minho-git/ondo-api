package com.ondo.wholesale.auth;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/wholesale/auth/login} · {@code /auth/logout} 엔드투엔드 (MUL-69).
 *
 * <p>앞 커밋들의 테스트는 자바 코드로 객체를 만들어 서비스에 직접 넘겼다. 여기서 처음으로
 * <b>진짜 요청이 진짜 DB 까지</b> 간다 — 봉투가 씌워지는지, 상태 코드가 맞는지,
 * 두 실패가 정말 똑같이 나가는지는 이 경로에서만 확인된다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다 — 실제 커밋 경계를 봐야 하기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginApiTest extends PostgresTestSupport {

    private static final String 비밀번호 = "Abcd1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void 도매처를_넣는다() {
        넣는다("pending@ondo.test", "PENDING", "1111111111");
        넣는다("approved@ondo.test", "APPROVED", "2222222222");
        넣는다("rejected@ondo.test", "REJECTED", "3333333333");
    }

    @AfterEach
    void 지운다() {
        // 세션을 안 지우면 앞 테스트가 남긴 세션이 다음 테스트 계정을 인증해버린다
        jdbc.execute("delete from wholesale.spring_session");
        jdbc.execute("delete from wholesale.approval_request");
        jdbc.execute("delete from wholesale.wholesaler_consent");
        jdbc.execute("delete from wholesale.wholesaler_document");
        jdbc.execute("delete from wholesale.wholesaler");
    }

    // ── 로그인 성공 ────────────────────────────────

    /** <b>AC 1</b> — 승인 상태가 응답에 담겨 온다. 프론트가 이걸로 진입 화면을 고른다. */
    @Test
    void 로그인에_성공하면_200과_data_봉투에_approvalStatus가_온다() throws Exception {
        로그인("approved@ondo.test", 비밀번호)
                .andExpect(status().isOk())
                // 봉투는 ApiResponseBodyAdvice 가 자동으로 씌운다
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                // 티켓이 못박은 것 — 세션 방식이라 토큰을 내려보내지 않는다
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    /** 심사 중·거절 계정도 로그인은 된다. 심사 현황 화면을 봐야 하기 때문이다. */
    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED"})
    void 승인되지_않은_계정도_로그인된다(String 상태) throws Exception {
        로그인(상태.toLowerCase() + "@ondo.test", 비밀번호)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value(상태));
    }

    @Test
    void 대문자로_보낸_이메일도_로그인된다() throws Exception {
        로그인("APPROVED@Ondo.TEST", 비밀번호)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    // ── 로그인 실패 ────────────────────────────────

    /**
     * <b>AC 2</b> — 없는 이메일과 틀린 비밀번호의 응답이 <b>글자까지 같아야</b> 한다.
     *
     * <p>코드만 맞춰놓고 문구가 갈리면 소용이 없다. 그래서 본문 전체를 비교한다.
     * traceId 는 요청마다 달라야 정상이므로 빼고 본다.
     */
    @Test
    void 없는_이메일과_틀린_비밀번호는_응답이_같다() throws Exception {
        String 없는_이메일 = 실패본문("nobody@ondo.test", 비밀번호);
        String 틀린_비밀번호 = 실패본문("approved@ondo.test", "틀린비밀번호!");

        assertThat(없는_이메일)
                .as("어느 쪽이 틀렸는지 응답으로 알 수 있으면 가입 여부가 새어나간다")
                .isEqualTo(틀린_비밀번호);
    }

    @Test
    void 로그인에_실패하면_401_LOGIN_FAILED_이고_data_봉투가_없다() throws Exception {
        로그인("nobody@ondo.test", 비밀번호)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** 값이 비어 있는 건 자격 문제가 아니라 형식 문제다 — 여기만 400 으로 갈린다. */
    @Test
    void 이메일이_비면_400_VALIDATION_FAILED() throws Exception {
        로그인("", 비밀번호)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── 로그아웃 ────────────────────────────────

    @Test
    void 로그아웃은_204이고_본문이_없다() throws Exception {
        mockMvc.perform(post("/api/wholesale/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    /** 세션이 없어도 204 다. 만료된 쿠키로 로그아웃해도 프론트가 따로 처리할 게 없어야 한다. */
    @Test
    void 세션이_없어도_로그아웃은_204다() throws Exception {
        mockMvc.perform(post("/api/wholesale/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // ── 도구 ────────────────────────────────

    private ResultActions 로그인(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/wholesale/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private String 실패본문(String email, String password) throws Exception {
        String 본문 = 로그인(email, password)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        return 본문.replaceAll("\"traceId\":\"[^\"]*\"", "\"traceId\":\"...\"");
    }

    private void 넣는다(String email, String 상태, String bizRegNo) {
        jdbc.update("""
                        insert into wholesale.wholesaler
                            (email, password_hash, biz_reg_no, biz_name, biz_owner_name, approval_status)
                        values (?, ?, ?, ?, ?, ?)""",
                email, passwordEncoder.encode(비밀번호), bizRegNo, "온도상사", "김대표", 상태);
    }
}
