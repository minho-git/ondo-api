package com.ondo.wholesale.security;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.support.ProtectedTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 게이트 통합 슬라이스(AC1). DB 비의존 @WebMvcTest.
 */
@WebMvcTest(ProtectedTestController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, ErrorResponseWriter.class, TraceIdFilter.class})
class AuthGateWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 비인증이면_401_UNAUTHENTICATED_공통포맷() throws Exception {
        mvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
