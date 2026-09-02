package com.ondo.wholesale.settlement;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 입금·미수 계약 스텁 대표 응답 검증 (MUL-83). 원장 meta.ledgerBalance 가 핵심이다. */
@WebMvcTest({PaymentController.class, ReceivableController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class SettlementStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 입금등록은_201로_선수금과_반영후_잔액을_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/payments")
                        .with(TestSecuritySupport.approved())
                        .header("Idempotency-Key", "01J9XKQ7ZC8N4T2V6M0P3RWXYZ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "retailerId": 3307, "amount": 400000, "paidAt": "2025-08-14T15:30:00+09:00",
                                  "paidBy": "RETAILER", "method": "CASH",
                                  "allocations": [ { "orderId": 5606, "amount": 71000 } ] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unallocatedAmount").value(0))
                .andExpect(jsonPath("$.data.ledgerBalance").value(-235000));
    }

    @Test
    void 미수_소매처목록은_부호있는_잔액과_페이징meta를_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables/retailers").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ledgerBalance").value(-589000))
                .andExpect(jsonPath("$.meta.totalElements").value(12));
    }

    @Test
    void 미수원장은_meta에_전체기준_현재잔액을_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].entryType").value("SALE"))
                .andExpect(jsonPath("$.data[2].paymentId").value(4398))
                .andExpect(jsonPath("$.meta.ledgerBalance").value(-235000))
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }
}
