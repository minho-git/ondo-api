package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소매 접점 계약 스텁 검증 (MUL-82). 도매 세션 없이 열리는 것은 U-14-B2 결정 전의
 * 잠정 조치이며, 그 잠정 상태 자체가 이 커밋의 계약이라 테스트로 고정한다.
 */
@WebMvcTest(RetailGatewayOrderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class RetailGatewayStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 소매주문생성은_도매세션없이_201로_NEW_주문을_내린다() throws Exception {
        mvc.perform(post("/api/retail-gateway/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "retailOrderId": 90210,
                                  "retailerId": 77,
                                  "wholesalerId": 12,
                                  "expectedPaymentMethod": "CASH",
                                  "receiveBy": "AGENT",
                                  "items": [ { "variantId": 1042, "qty": 3, "expectedUnitPrice": 5000 } ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.retailOrderId").value(90210))
                .andExpect(jsonPath("$.data.orderAmount").value(25000))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5000));
    }
}
