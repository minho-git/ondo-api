package com.ondo.wholesale.order.controller;

import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.SettlementStatus;
import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.error.GlobalExceptionHandler;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.order.dto.response.OrderFilterResponse;
import com.ondo.wholesale.order.dto.response.OrderStatusResponse;
import com.ondo.wholesale.order.dto.response.OrderSummaryResponse;
import com.ondo.wholesale.order.service.OrderQueryService;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 조회 API 의 HTTP 계층 검증 (MUL-47) — 봉투·400/404 매핑.
 * 데이터 조립은 통합 테스트({@code OrderQueryIntegrationTest})가 본다.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class, GlobalExceptionHandler.class})
class OrderQueryApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void 주문목록은_data배열과_페이징meta를_함께_내린다() throws Exception {
        OrderSummaryResponse row = new OrderSummaryResponse(
                1L, 1, OffsetDateTime.now(), 701L, "행복상회", "니트", 1, 13000,
                new OrderStatusResponse(OrderStatusKey.NEW, "신규 주문"),
                SettlementStatus.UNPAID, 0, true, true, false);
        given(orderQueryService.list(anyLong(), any()))
                .willReturn(ApiResponse.paged(List.of(row), new ApiResponse.PageMeta(0, 20, 1, 1)));

        mvc.perform(get("/api/wholesale/orders").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].summaryProductName").value("니트"))
                .andExpect(jsonPath("$.data[0].isConfirmable").value(true))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void 미정의_filter_값은_400_VALIDATION_FAILED다() throws Exception {
        mvc.perform(get("/api/wholesale/orders?filter=NOPE").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("filter"));
    }

    @Test
    void size가_100을_넘으면_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/orders?size=101").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void 상태칩은_meta_없이_배열만_내린다() throws Exception {
        given(orderQueryService.filters(anyLong(), any(), any(), any()))
                .willReturn(List.of(new OrderFilterResponse(OrderFilterKey.ALL, "전체", 5)));

        mvc.perform(get("/api/wholesale/orders/filters").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data[0].key").value("ALL"))
                .andExpect(jsonPath("$.data[0].count").value(5));
    }

    @Test
    void 없는_주문_상세는_404_RESOURCE_NOT_FOUND다() throws Exception {
        given(orderQueryService.detail(anyLong(), eq(999L)))
                .willThrow(new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다."));

        mvc.perform(get("/api/wholesale/orders/999").with(TestSecuritySupport.approved()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
