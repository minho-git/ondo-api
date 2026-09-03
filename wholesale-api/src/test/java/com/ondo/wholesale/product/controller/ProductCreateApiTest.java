package com.ondo.wholesale.product.controller;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 등록의 HTTP 계층 계약(MUL-91) — 형식 검증(Bean Validation)·에러 매핑·봉투만 본다.
 * 정책 검증과 저장은 통합 테스트({@code ProductCreateIntegrationTest}) 몫이다.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        GlobalExceptionHandler.class, TraceIdFilter.class})
class ProductCreateApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    void 이름이_비면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "categoryId": 121,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}], "listing": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void categoryId가_없으면_VALIDATION_FAILED_400이다() throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "오버핏 코튼 티셔츠", "categoryId": null,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}], "listing": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 등록은_201로_data봉투에_상세를_담는다() throws Exception {
        when(productCommandService.create(eq(1L), any())).thenReturn(ProductStubExamples.productDetail());

        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "오버핏 코튼 티셔츠", "categoryId": 121,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}], "listing": null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5012))
                .andExpect(jsonPath("$.data.productNumber").value(18));
    }

    @Test
    void 서비스의_CATEGORY_NOT_LEAF는_400코드로_나간다() throws Exception {
        when(productCommandService.create(eq(1L), any()))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NOT_LEAF));

        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "오버핏 코튼 티셔츠", "categoryId": 12,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}], "listing": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));
    }
}
