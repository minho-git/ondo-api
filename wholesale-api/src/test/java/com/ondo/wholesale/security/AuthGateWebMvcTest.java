package com.ondo.wholesale.security;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.support.ProtectedTestController;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증·승인 게이트 통합 슬라이스(AC1·AC2·AC3). DB 비의존 @WebMvcTest.
 */
@WebMvcTest(ProtectedTestController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class AuthGateWebMvcTest {

    @Autowired
    private MockMvc mvc;

    // ── AC1: 세션 없음 → 401 ──────────────────────────────
    @Test
    void 비인증이면_401_UNAUTHENTICATED_공통포맷() throws Exception {
        mvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ── AC2: PENDING → 403, 단 /me·/me/reapply는 통과 ──────
    @Test
    void 미승인_PENDING이면_보호경로_403_NOT_APPROVED() throws Exception {
        mvc.perform(get("/test/protected").with(TestSecuritySupport.pending()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_APPROVED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void PENDING이어도_me는_통과한다() throws Exception {
        mvc.perform(get("/api/wholesale/me").with(TestSecuritySupport.pending()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("me"));
    }

    @Test
    void PENDING이어도_me_reapply는_통과한다() throws Exception {
        mvc.perform(post("/api/wholesale/me/reapply").with(TestSecuritySupport.pending()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("reapplied"));
    }

    // ── AC3: APPROVED → 200 data 봉투 ─────────────────────
    @Test
    void 승인_APPROVED면_보호경로_200_data봉투() throws Exception {
        mvc.perform(get("/test/protected").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("protected"))
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }

    // ── 방어: WholesalePrincipal이 아니면 일반 권한부족 403 ──
    @Test
    void WholesalePrincipal이_아니면_403_ACCESS_DENIED() throws Exception {
        mvc.perform(get("/test/protected").with(TestSecuritySupport.otherPrincipal()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
