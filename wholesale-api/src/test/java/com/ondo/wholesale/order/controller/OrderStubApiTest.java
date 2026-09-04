package com.ondo.wholesale.order.controller;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import com.ondo.wholesale.order.service.OrderQueryService;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 계약 스텁 대표 응답 검증 (MUL-82). example 전수 검증이 아니라
 * 봉투·페이징 meta·계약 핵심 필드(상태 key/label, 버튼 boolean, 수량 스코프)만 본다.
 */
@WebMvcTest({OrderController.class, PackingController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class OrderStubApiTest {

    @Autowired
    private MockMvc mvc;

    /** 조회는 실구현으로 교체됐다(MUL-47) — 남은 명령 스텁만 보는 테스트라 조회 서비스는 모킹한다. */
    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void 주문확정은_상세와_동일한_스키마로_배분과_미송_현황을_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/orders/5531/confirm")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "orderItemId": 88102, "allocateQty": 6 } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.key").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].allocatedQty").value(6))
                .andExpect(jsonPath("$.data.items[0].unallocatedQty").value(4))
                .andExpect(jsonPath("$.data.items[0].backorderQty").value(4));
    }

    @Test
    void 주문취소는_CANCELLED_상태의_상세를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/orders/5531/cancel").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.key").value("CANCELLED"))
                .andExpect(jsonPath("$.data.confirmedAt").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].backorderQty").value(0));
    }

    @Test
    void 포장준비는_201로_생성된_카드_한장을_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/orders/5531/packings")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "orderItemId": 88102, "allocateQty": 4 } ] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.outboundId").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].qty").value(4));
    }

    @Test
    void 배분취소는_204_본문없음이다() throws Exception {
        mvc.perform(delete("/api/wholesale/packings/7703").with(TestSecuritySupport.approved()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
