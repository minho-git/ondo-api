package com.ondo.wholesale.product.controller;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.error.GlobalExceptionHandler;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.product.service.ProductCommandService;
import com.ondo.wholesale.product.service.ProductQueryService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 수정의 HTTP 계층 계약 (MUL-93) — PATCH 의미론의 형식 규칙만 본다.
 * "생략 = 무변경, 명시적 null = 400" 구분과 variantPrice 지정 방식의 형식 검증.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        GlobalExceptionHandler.class, TraceIdFilter.class})
class ProductUpdateApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    void 최상위_필드에_명시적_null을_보내면_400이다() throws Exception {
        mvc.perform(patch("/api/wholesale/products/1").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listing에_명시적_null을_보내면_400이다() throws Exception {
        // 게시글 삭제 의도로 null 을 보낼 수 있지만, 게시글만 지우는 길은 없다 — 상품 삭제뿐
        mvc.perform(patch("/api/wholesale/products/1").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listing\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void variantPrice가_variantId와_색사이즈를_같이_보내면_400이다() throws Exception {
        mvc.perform(patch("/api/wholesale/products/1").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listing": {"title": "제목", "variantPrices": [
                                    {"variantId": 1, "colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0}]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void variantPrice가_지정_없이_오면_400이다() throws Exception {
        mvc.perform(patch("/api/wholesale/products/1").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listing": {"title": "제목", "variantPrices": [
                                    {"salePrice": 29000, "orderLimit": 0}]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
