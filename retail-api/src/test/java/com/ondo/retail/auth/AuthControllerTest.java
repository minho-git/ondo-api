package com.ondo.retail.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP 계약을 잡는다 — 상태코드와 응답 모양.
 *
 * <p>세션 쿠키는 여기서 못 본다. {@code server.servlet.session.cookie.*} 는 서블릿 컨테이너를
 * 거쳐 적용되는데 MockMvc 는 컨테이너 없이 돈다. 쿠키는 {@link AuthSessionTest} 가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "ondo.storage.local-root=${java.io.tmpdir}/ondo-test-uploads")
class AuthControllerTest {

    private static final String LOGIN_BODY = """
            {"email":"bombom@ondo.test","password":"ondo1234!"}""";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("로그인하면 200 과 계정 정보가 온다")
    void 로그인() throws Exception {
        mvc.perform(post("/api/retail/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retailerId").exists())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("쿠키 없이 부르면 401 이고 우리 에러 모양이다")
    void 인증_없이() throws Exception {
        mvc.perform(get("/api/retail/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").isArray())   // 배열은 null 이 아니라 [] 다
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 INVALID_CREDENTIALS")
    void 비밀번호_틀림() throws Exception {
        mvc.perform(post("/api/retail/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bombom@ondo.test","password":"틀린비번"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("없는 이메일도 같은 401 이다")
    void 없는_이메일() throws Exception {
        mvc.perform(post("/api/retail/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"없는사람@ondo.test","password":"ondo1234!"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("빈 값이면 400 이고 어느 칸이 왜 걸렸는지 나온다")
    void 검증_실패() throws Exception {
        mvc.perform(post("/api/retail/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.errors[0].code").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("가입하면 201 이고 세션을 주지 않는다")
    void 가입() throws Exception {
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.APPLICATION_JSON_VALUE,
                """
                {"email":"mvc-signup@ondo.test","password":"ondo1234!","shopName":"새싹상회",
                 "ownerName":"박새싹","mobile":"01011112222","bizRegNo":"1112223333",
                 "agreedTerms":["SERVICE","PRIVACY"]}""".getBytes());
        MockMultipartFile 등록증 = new MockMultipartFile("bizLicense", "biz.png",
                MediaType.IMAGE_PNG_VALUE, new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        MvcResult result = mvc.perform(multipart("/api/retail/auth/sign-up").file(payload).file(등록증))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.appliedAt").exists())
                .andReturn();

        assertThat(result.getResponse().getCookie("SESSION_RETAIL")).isNull();
    }

    @Test
    @DisplayName("이메일 중복 확인은 로그인 없이 부를 수 있다")
    void 이메일_중복_확인() throws Exception {
        mvc.perform(get("/api/retail/auth/email-availability").param("email", "bombom@ondo.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAvailable").value(false));

        mvc.perform(get("/api/retail/auth/email-availability").param("email", "아무도안쓴@ondo.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAvailable").value(true));
    }
}
