package com.ondo.wholesale.product;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 계약 스텁 대표 응답 검증 (MUL-81). example 값 전수 검증이 아니라
 * "봉투·페이징 meta·계약 핵심 필드가 계약서 형태로 나가는지"만 본다.
 */
@WebMvcTest({ProductController.class, ListingController.class, ColorController.class, CategoryController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class ProductStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 상품목록은_data배열과_페이징meta를_함께_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productNumber").value(18))
                .andExpect(jsonPath("$.data[0].categoryPath.length()").value(3))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(137));
    }

    @Test
    void 상품상세는_data봉투에_색상별SKU와_게시글을_담는다() throws Exception {
        mvc.perform(get("/api/wholesale/products/5012").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(5012))
                .andExpect(jsonPath("$.data.colorOptions[0].color.id").value(1))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].size").value("XS"))
                .andExpect(jsonPath("$.data.listing.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.listing.isSinglePieceAllowed").value(true));
    }

    @Test
    void 상품등록은_201로_상세와_동일한_스키마를_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/products")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "루즈핏 오버핏 셔츠",
                                  "categoryId": 312,
                                  "colorOptions": [ { "colorId": 1, "sizes": ["FREE", "2XL"] } ],
                                  "listing": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productNumber").value(18))
                .andExpect(jsonPath("$.data.listing.status").value("ON_SALE"));
    }

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
