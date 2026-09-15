package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.error.GlobalExceptionHandler;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import com.ondo.wholesale.order.ReceiveBy;
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
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대시보드 summary API 의 HTTP 계층 검증 (MUL-120) — 봉투와 응답 필드 계약.
 * 집계 값 자체는 통합 테스트가 본다. 401/403 게이트는 {@code AuthGateWebMvcTest} 전담.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class, GlobalExceptionHandler.class})
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardQueryService dashboardQueryService;

    @Test
    void 요약_숫자를_봉투에_담아_내린다() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 10, 21, 42, 0, 0, ZoneOffset.ofHours(9));
        // summary(7L) 에만 스터빙 — principal 의 도매처 id 가 그대로 전달되는지도 함께 확인된다
        given(dashboardQueryService.summary(7L)).willReturn(new DashboardSummaryResponse(
                now,
                new DashboardSummaryResponse.NewOrders(4, now.minusMinutes(42), "봄봄"),
                new DashboardSummaryResponse.Packing(3, 27, Map.of(ReceiveBy.AGENT, 2, ReceiveBy.RETAILER, 1)),
                new DashboardSummaryResponse.Outbound(5, 2),
                new DashboardSummaryResponse.Backorder(12, 86, 3, 4),
                new DashboardSummaryResponse.Today(
                        new DashboardSummaryResponse.Today.Orders(18, 1_284_000), 1,
                        new DashboardSummaryResponse.Today.Shipped(11, 143))));

        mvc.perform(get("/api/wholesale/dashboard/summary").with(TestSecuritySupport.approvedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.now").exists())
                .andExpect(jsonPath("$.data.newOrders.count").value(4))
                .andExpect(jsonPath("$.data.newOrders.oldestRetailerName").value("봄봄"))
                .andExpect(jsonPath("$.data.packing.retailerCount").value(3))
                .andExpect(jsonPath("$.data.packing.byReceive.AGENT").value(2))
                .andExpect(jsonPath("$.data.packing.byReceive.RETAILER").value(1))
                .andExpect(jsonPath("$.data.outbound.notShippedCount").value(5))
                .andExpect(jsonPath("$.data.outbound.staleCount").value(2))
                .andExpect(jsonPath("$.data.backorder.skuCount").value(12))
                .andExpect(jsonPath("$.data.backorder.qty").value(86))
                .andExpect(jsonPath("$.data.backorder.overdueSkuCount").value(3))
                .andExpect(jsonPath("$.data.backorder.noDateSkuCount").value(4))
                .andExpect(jsonPath("$.data.today.orders.count").value(18))
                .andExpect(jsonPath("$.data.today.orders.amount").value(1_284_000))
                .andExpect(jsonPath("$.data.today.cancelled").value(1))
                .andExpect(jsonPath("$.data.today.shipped.count").value(11))
                .andExpect(jsonPath("$.data.today.shipped.qty").value(143));
    }
}
