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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 목록·단건 조회의 HTTP 계층 계약 (MUL-92) — 쿼리 파라미터 형식 400만 본다.
 * 실제 검색·정렬·집계는 통합 테스트({@code ProductQueryIntegrationTest}) 몫이다.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        GlobalExceptionHandler.class, TraceIdFilter.class})
class ProductQueryApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    void size가_100을_넘으면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/products?size=101").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void 날짜_형식이_틀리면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/products?from=2026/09/01").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    void from이_to보다_뒤면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/products?from=2026-09-03&to=2026-09-01")
                        .with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void sort_방향이_asc도_desc도_아니면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/products?sort=name,sideways").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 모르는_sort_키는_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/products?sort=hack,desc").with(TestSecuritySupport.approved()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
