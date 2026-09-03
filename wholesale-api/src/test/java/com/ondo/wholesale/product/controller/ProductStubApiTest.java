package com.ondo.wholesale.product.controller;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.product.service.ProductCommandService;
import com.ondo.wholesale.product.service.ProductQueryService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 계약 스텁 대표 응답 검증 (MUL-81). example 값 전수 검증이 아니라
 * "봉투·페이징 meta·계약 핵심 필드가 계약서 형태로 나가는지"만 본다.
 */
@WebMvcTest({ProductController.class, ListingController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class ProductStubApiTest {

    @Autowired
    private MockMvc mvc;

    // 등록(MUL-91)·목록·상세(MUL-92)는 실구현으로 빠졌다 — 남은 스텁 엔드포인트를 띄우기 위한 목.
    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductQueryService productQueryService;




    @Test
    void 시즌종료는_전이후_listing스키마를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/listings/4410/season-end").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEASON_ENDED"))
                .andExpect(jsonPath("$.data.seasonEndedAt").isNotEmpty());
    }

    @Test
    void 상품삭제는_204_본문없음이다() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/wholesale/products/5012").with(TestSecuritySupport.approved()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
