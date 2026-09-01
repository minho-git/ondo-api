package com.ondo.wholesale.auth;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/wholesale/auth/signup} 엔드투엔드 (MUL-68).
 *
 * <p>여기서만 확인할 수 있는 것들이 있다 — 비인증으로 뚫리는지, 응답이 data 봉투에
 * 담기는지, JSON 을 객체로 바꾸는 단계에서 필드를 통째로 빠뜨렸을 때 어떻게 되는지.
 * 앞 커밋들의 테스트는 전부 자바 코드로 객체를 만들어 넘겨서 이 경로를 안 탔다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다 — 실제 커밋 경계를 봐야 하기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupApiTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void 지운다() {
        jdbc.execute("delete from wholesale.approval_request");
        jdbc.execute("delete from wholesale.wholesaler_consent");
        jdbc.execute("delete from wholesale.wholesaler_document");
        jdbc.execute("delete from wholesale.wholesaler");
    }

    @Test
    void 가입에_성공하면_201과_data_봉투가_온다() throws Exception {
        보낸다(요청("owner@ondo.test", "Abcd1234!", "1234567890"))
                .andExpect(status().isCreated())
                // 봉투는 ApiResponseBodyAdvice 가 자동으로 씌운다
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.bizName").value("온도상사"))
                .andExpect(jsonPath("$.data.bizRegNo").value("1234567890"))
                .andExpect(jsonPath("$.data.appliedAt").exists());
    }

    @Test
    void 로그인하지_않아도_가입할_수_있다() throws Exception {
        // SecurityConfig 가 /api/wholesale/auth/** 를 permitAll 로 열어둔 게 실제로 먹는지
        보낸다(요청("noauth@ondo.test", "Abcd1234!", "1234567890"))
                .andExpect(status().isCreated());
    }

    @Test
    void 가입해도_세션은_주지_않는다() throws Exception {
        MvcResult result = 보낸다(요청("nosession@ondo.test", "Abcd1234!", "1234567890"))
                .andExpect(status().isCreated())
                .andReturn();

        // 가입 직후는 늘 PENDING 이라 로그인 화면으로 보낸다. 세션은 로그인(MUL-69)에서 준다
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void 이메일_형식이_틀리면_400_VALIDATION_FAILED() throws Exception {
        보낸다(요청("ondo.test", "Abcd1234!", "1234567890"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("email")))
                // 에러는 data 봉투를 쓰지 않는다
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void 비밀번호가_정책에_맞지_않으면_400_PASSWORD_POLICY_VIOLATED() throws Exception {
        보낸다(요청("weak@ondo.test", "abcdefgh", "1234567890"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATED"));
    }

    @Test
    void 필수_동의를_안_하면_400_REQUIRED_CONSENT_MISSING() throws Exception {
        String json = """
                {
                  "email": "noconsent@ondo.test", "password": "Abcd1234!",
                  "phone": "01012345678", "bizRegNo": "1234567890",
                  "bizName": "온도상사", "bizOwnerName": "김대표",
                  "documents": [
                    { "type": "BIZ_REG", "fileKey": "uploads/2026/09/a.jpg" },
                    { "type": "CEO_ID",  "fileKey": "uploads/2026/09/b.jpg" }
                  ],
                  "consents": [
                    { "type": "TERMS", "agreed": false },
                    { "type": "PRIVACY", "agreed": true },
                    { "type": "INFO_CONFIRM", "agreed": true },
                    { "type": "MARKETING_SMS", "agreed": false },
                    { "type": "MARKETING_EMAIL", "agreed": false },
                    { "type": "MARKETING_PUSH", "agreed": false }
                  ]
                }
                """;

        보낸다(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"))
                .andExpect(jsonPath("$.errors[0].reason").value(
                        org.hamcrest.Matchers.containsString("TERMS")));
    }

    @Test
    void 서류_필드를_통째로_빼면_400_REQUIRED_DOCUMENT_MISSING() throws Exception {
        // JSON 에 documents 키가 아예 없는 경우. 자바 코드로 객체를 만들 땐 나오지 않는 상황이다
        String json = """
                {
                  "email": "nodoc@ondo.test", "password": "Abcd1234!",
                  "phone": "01012345678", "bizRegNo": "1234567890",
                  "bizName": "온도상사", "bizOwnerName": "김대표",
                  "consents": [
                    { "type": "TERMS", "agreed": true },
                    { "type": "PRIVACY", "agreed": true },
                    { "type": "INFO_CONFIRM", "agreed": true },
                    { "type": "MARKETING_SMS", "agreed": false },
                    { "type": "MARKETING_EMAIL", "agreed": false },
                    { "type": "MARKETING_PUSH", "agreed": false }
                  ]
                }
                """;

        보낸다(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_DOCUMENT_MISSING"));
    }

    @Test
    void 이미_가입된_이메일이면_400_EMAIL_DUPLICATED() throws Exception {
        보낸다(요청("dup@ondo.test", "Abcd1234!", "1000000001"))
                .andExpect(status().isCreated());

        보낸다(요청("dup@ondo.test", "Abcd1234!", "2000000002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    void 이미_가입된_사업자번호면_400_BIZ_REG_NO_DUPLICATED() throws Exception {
        보낸다(요청("first@ondo.test", "Abcd1234!", "1000000001"))
                .andExpect(status().isCreated());

        보낸다(요청("second@ondo.test", "Abcd1234!", "1000000001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_REG_NO_DUPLICATED"));
    }

    @Test
    void 대문자로_보낸_이메일은_소문자로_저장된다() throws Exception {
        보낸다(요청("Owner@Ondo.TEST", "Abcd1234!", "1234567890"))
                .andExpect(status().isCreated());

        String email = jdbc.queryForObject(
                "select email from wholesale.wholesaler", String.class);
        assertThat(email).isEqualTo("owner@ondo.test");
    }

    @Test
    void 모르는_필드를_보내도_통과한다() throws Exception {
        // "주요 취급 카테고리" 는 저장할 자리가 없어 DTO 에 넣지 않았다.
        // 프론트가 보내도 400 이 나면 안 된다
        String json = 요청("extra@ondo.test", "Abcd1234!", "1234567890")
                .replace("\"bizName\"", "\"mainCategory\": \"여성의류\", \"bizName\"");

        보낸다(json).andExpect(status().isCreated());
    }

    // ── 도우미 ──────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions 보낸다(String json) throws Exception {
        return mockMvc.perform(post("/api/wholesale/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String 요청(String email, String password, String bizRegNo) {
        return """
                {
                  "email": "%s", "password": "%s",
                  "phone": "01012345678", "bizRegNo": "%s",
                  "bizName": "온도상사", "bizOwnerName": "김대표",
                  "storePhone": "0212345678", "storeBuilding": "누죤",
                  "storeUnit": "3층 C-25", "bizCategory": "도매 및 소매업",
                  "documents": [
                    { "type": "BIZ_REG", "fileKey": "uploads/2026/09/ab12cd34.jpg" },
                    { "type": "CEO_ID",  "fileKey": "uploads/2026/09/ef56gh78.jpg" }
                  ],
                  "consents": [
                    { "type": "TERMS", "agreed": true },
                    { "type": "PRIVACY", "agreed": true },
                    { "type": "INFO_CONFIRM", "agreed": true },
                    { "type": "MARKETING_SMS", "agreed": false },
                    { "type": "MARKETING_EMAIL", "agreed": false },
                    { "type": "MARKETING_PUSH", "agreed": false }
                  ]
                }
                """.formatted(email, password, bizRegNo);
    }
}
