package com.ondo.wholesale.config;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import com.ondo.wholesale.security.support.ProtectedTestController;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 계약 (MUL-86) — 프론트 dev 서버(3000)의 preflight 와 자격증명 요청이 통과하고,
 * 허용 목록 밖 origin 은 차단되는지 본다.
 */
@WebMvcTest(ProtectedTestController.class)
@Import({SecurityConfig.class, CorsConfig.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ApprovedAuthorizationManager.class, ErrorResponseWriter.class,
        ApiResponseBodyAdvice.class, TraceIdFilter.class})
@TestPropertySource(properties = "ondo.cors.allowed-origins=http://localhost:3000")
class CorsWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 허용_origin의_preflight는_인증없이_통과한다() throws Exception {
        mvc.perform(options("/api/wholesale/me")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void 허용_origin의_실요청에는_CORS_헤더가_붙는다() throws Exception {
        mvc.perform(get("/api/wholesale/me")
                        .header("Origin", "http://localhost:3000")
                        .with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void 허용목록_밖_origin은_차단된다() throws Exception {
        mvc.perform(options("/api/wholesale/me")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
