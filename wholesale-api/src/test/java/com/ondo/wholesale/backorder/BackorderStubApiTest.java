package com.ondo.wholesale.backorder;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미송 계약 스텁 대표 응답 검증 (MUL-83). 조회 2종은 실구현(MUL-48)으로 교체돼
 * 통합 테스트가 대신 본다 — 남은 스텁이 전부 교체되면 이 클래스는 없어진다.
 */
@WebMvcTest(BackorderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class BackorderStubApiTest {

    @Autowired
    private MockMvc mvc;

    /** 조회·배분은 실구현(통합 테스트 범위) — 컨트롤러 생성에만 필요해 모킹한다. */
    @MockitoBean
    private BackorderQueryService backorderQueryService;

    @MockitoBean
    private BackorderAllocationService backorderAllocationService;

    @Test
    void 예상입고일_등록은_저장값을_그대로_돌려준다() throws Exception {
        mvc.perform(put("/api/wholesale/variants/90231/expected-inbound")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "expectedInboundDate": "2024-07-15", "expectedInboundReason": "공장 생산 일정이 3일 밀려요." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedInboundDate").value("2024-07-15"));
    }
}
