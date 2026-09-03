package com.ondo.wholesale.product;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 등록 흐름 검증 (MUL-91) — V5 시드(카테고리 리프 121=티셔츠, 색 1=블랙·7=베이지) 위에서
 * 품번 채번, 게시글 동시 생성, 정책 에러 코드, 서버 보장 정렬을 실DB 로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductCreateIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long wholesalerId;

    @BeforeEach
    void 도매처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "create@ondo.test", "9300000001");
    }

    @Test
    void 상품만_등록하면_listing이_null이고_품번이_도매처별로_1씩_는다() throws Exception {
        등록한다("{\"name\": \"첫 상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productNumber").value(1))
                .andExpect(jsonPath("$.data.listing").value((Object) null))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value((Object) null));

        등록한다("{\"name\": \"둘째 상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}")
                .andExpect(jsonPath("$.data.productNumber").value(2));

        // 다른 도매처는 자기 연번 1부터
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other@ondo.test", "9300000002");
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"남의 상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}"))
                .andExpect(jsonPath("$.data.productNumber").value(1));
    }

    @Test
    void listing과_함께_등록하면_게시글과_전_variant_판매가가_한_번에_생긴다() throws Exception {
        등록한다("""
                {"name": "게시 상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 1, "sizes": ["S", "M"]}],
                 "listing": {"title": "[신상] 게시 상품", "description": "설명", "isSinglePieceAllowed": true,
                             "images": ["https://cdn.ondo.example/1.jpg", "https://cdn.ondo.example/2.jpg"],
                             "variantPrices": [
                                 {"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0},
                                 {"colorId": 1, "size": "M", "salePrice": 29000, "orderLimit": 50}]}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.listing.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.listing.title").value("[신상] 게시 상품"))
                .andExpect(jsonPath("$.data.listing.seasonStartedAt").exists())
                .andExpect(jsonPath("$.data.listing.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[1].orderLimit").value(50));

        Integer priceRows = jdbc.queryForObject(
                "select count(*) from wholesale.listing_variant", Integer.class);
        assertThat(priceRows).isEqualTo(2);
    }

    @Test
    void colorOptions가_비면_OPTION_REQUIRED다() throws Exception {
        등록한다("{\"name\": \"상품\", \"categoryId\": 121, \"colorOptions\": [], \"listing\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPTION_REQUIRED"));
    }

    @Test
    void 색이_중복되면_COLOR_DUPLICATED다() throws Exception {
        등록한다("""
                {"name": "상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}, {"colorId": 1, "sizes": ["M"]}], "listing": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COLOR_DUPLICATED"));
    }

    @Test
    void 한_색에_같은_사이즈면_SIZE_DUPLICATED다() throws Exception {
        등록한다("{\"name\": \"상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\", \"S\"]}], \"listing\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIZE_DUPLICATED"));
    }

    @Test
    void 없는_카테고리는_CATEGORY_NOT_FOUND다() throws Exception {
        등록한다("{\"name\": \"상품\", \"categoryId\": 999999, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void 리프가_아닌_카테고리는_CATEGORY_NOT_LEAF다() throws Exception {
        // 12 = 여성 > 상의 (depth 2, 자식 있음)
        등록한다("{\"name\": \"상품\", \"categoryId\": 12, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));
    }

    @Test
    void variantPrices에_빠진_variant가_있으면_PRICE_REQUIRED다() throws Exception {
        등록한다("""
                {"name": "상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 1, "sizes": ["S", "M"]}],
                 "listing": {"title": "제목", "description": null, "isSinglePieceAllowed": false, "images": [],
                             "variantPrices": [{"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0}]}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));
    }

    @Test
    void variantPrices가_없는_조합을_가리키면_INVARIANT_VIOLATED다() throws Exception {
        등록한다("""
                {"name": "상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}],
                 "listing": {"title": "제목", "description": null, "isSinglePieceAllowed": false, "images": [],
                             "variantPrices": [
                                 {"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0},
                                 {"colorId": 7, "size": "M", "salePrice": 29000, "orderLimit": 0}]}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATED"));
    }

    @Test
    void 등록에서_variantId를_지정하면_VALIDATION_FAILED다() throws Exception {
        등록한다("""
                {"name": "상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}],
                 "listing": {"title": "제목", "description": null, "isSinglePieceAllowed": false, "images": [],
                             "variantPrices": [{"variantId": 1, "salePrice": 29000, "orderLimit": 0}]}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 응답의_variant는_사이즈_선언_순으로_정렬된다() throws Exception {
        등록한다("{\"name\": \"상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"FREE\", \"2XL\", \"S\"]}], \"listing\": null}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].size").value("S"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[1].size").value("2XL"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[2].size").value("FREE"));
    }

    @Test
    void 색상은_그룹과_그룹내_순서로_정렬된다() throws Exception {
        // 7 = 베이지(베이지·브라운 그룹) — 무채색(블랙 1)보다 뒤 그룹
        등록한다("""
                {"name": "상품", "categoryId": 121,
                 "colorOptions": [{"colorId": 7, "sizes": ["S"]}, {"colorId": 1, "sizes": ["S"]}], "listing": null}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.colorOptions[0].color.name").value("블랙"))
                .andExpect(jsonPath("$.data.colorOptions[1].color.name").value("베이지"))
                .andExpect(jsonPath("$.data.colorOptions[1].color.groupName").value("베이지·브라운"));
    }

    private ResultActions 등록한다(String body) throws Exception {
        return mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
