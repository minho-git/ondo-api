package com.ondo.wholesale.outbound;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.outbound.controller.OutboundController;
import com.ondo.wholesale.outbound.service.OutboundCommandService;
import com.ondo.wholesale.outbound.service.OutboundQueryService;
import com.ondo.wholesale.outbound.service.PackingQueueQueryService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 출고 계약 스텁 대표 응답 검증 (MUL-83). 실구현으로 교체된 엔드포인트의 검증은
 * 통합 테스트로 넘어갔고, 여기는 아직 스텁인 전이 계약(포장완료·확정·장끼)만 본다.
 */
@WebMvcTest(OutboundController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class OutboundStubApiTest {

    @Autowired
    private MockMvc mvc;

    // 실구현으로 교체된 엔드포인트의 협력자 — 남은 스텁 검증에는 쓰지 않는다
    @MockitoBean
    private PackingQueueQueryService packingQueueQueryService;
    @MockitoBean
    private OutboundCommandService outboundCommandService;
    @MockitoBean
    private OutboundQueryService outboundQueryService;

    @Test
    void 출고확정은_shippedAt과_장끼번호를_채워_상세를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/outbounds/8801/ship").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.statementNumber").value(1))
                .andExpect(jsonPath("$.data.isShippable").value(false));
    }

    @Test
    void 장끼는_장끼번호와_출고일시를_항상_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds/8801/statement").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNumber").value(1))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }
}
