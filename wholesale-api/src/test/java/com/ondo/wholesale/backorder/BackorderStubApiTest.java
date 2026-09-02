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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 미송 계약 스텁 대표 응답 검증 (MUL-83). data+stats 봉투가 핵심이다. */
@WebMvcTest(BackorderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class BackorderStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 미송SKU목록은_data배열과_페이징meta를_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/backorders/variants").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].backorderQty").value(90))
                .andExpect(jsonPath("$.meta.totalElements").value(12));
    }

    @Test
    void SKU별미송은_meta없이_data와_stats를_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/variants/90231/backorders").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data[0].remainingQty").value(12))
                .andExpect(jsonPath("$.stats.backorderQty").value(90))
                .andExpect(jsonPath("$.stats.backorderAmount").value(1520000));
    }

    @Test
    void 미송배분은_201로_주문별_카드와_해소된_미송id를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/backorders/allocations")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "backorderId": 6101, "allocateQty": 12 } ] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packings[0].status").value("READY"))
                .andExpect(jsonPath("$.data.resolvedBackorderIds[0]").value(6101));
    }

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
