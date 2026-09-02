package com.ondo.wholesale.outbound;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 출고 계약 스텁 대표 응답 검증 (MUL-83). 헤더/펼침의 봉투 차이와 전이 계약을 본다. */
@WebMvcTest(OutboundController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class OutboundStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 포장대기_소매처목록은_페이징없이_집계를_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/packing-items/retailers").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data[0].itemCount").value(6));
    }

    @Test
    void 봉투목록은_data배열과_페이징meta를_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].shippedAt").value((Object) null))
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void 포장완료는_201로_PACKED_포장이_담긴_봉투를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/outbounds")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"packingItemIds\": [91101] }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippedAt").value((Object) null))
                .andExpect(jsonPath("$.data.statementNumber").value((Object) null))
                .andExpect(jsonPath("$.data.packings[0].status").value("PACKED"));
    }

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
