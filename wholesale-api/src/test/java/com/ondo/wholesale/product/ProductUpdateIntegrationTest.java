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
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 수정 1단계 흐름 검증 (MUL-93) — name·categoryId 수정과 listing upsert.
 * PATCH 의미론: 생략 = 무변경, listing 내부는 null = 무변경(팀 결정 2026-09-04),
 * images·variantPrices 는 보낸 경우에만 전체 교체.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductUpdateIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long wholesalerId;

    @BeforeEach
    void 도매처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "update@ondo.test", "9500000001");
    }

    // ── 생략 = 무변경 ─────────────────────────────

    @Test
    void 빈_body면_아무것도_안_바뀐다() throws Exception {
        long id = 게시글까지_등록한다("그대로 상품", "그대로 게시");

        수정한다(id, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("그대로 상품"))
                .andExpect(jsonPath("$.data.listing.title").value("그대로 게시"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000));
    }

    @Test
    void 이름만_보내면_이름만_바뀌고_게시글은_그대로다() throws Exception {
        long id = 게시글까지_등록한다("옛 이름", "원래 제목");

        수정한다(id, "{\"name\": \"새 이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새 이름"))
                .andExpect(jsonPath("$.data.listing.title").value("원래 제목"))
                .andExpect(jsonPath("$.data.listing.images", hasSize(2)))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000));

        Integer priceRows = jdbc.queryForObject("select count(*) from wholesale.listing_variant", Integer.class);
        assertThat(priceRows).isEqualTo(2);
    }

    @Test
    void 이름을_공백으로_바꾸면_400이다() throws Exception {
        long id = 상품만_등록한다("멀쩡한 이름");

        수정한다(id, "{\"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String name = jdbc.queryForObject("select name from wholesale.product where id = ?", String.class, id);
        assertThat(name).isEqualTo("멀쩡한 이름");
    }

    @Test
    void 카테고리는_리프로만_바꿀_수_있다() throws Exception {
        long id = 상품만_등록한다("카테고리 상품");

        수정한다(id, "{\"categoryId\": 12}") // 여성 > 상의 (depth 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));

        수정한다(id, "{\"categoryId\": 162}") // 여성 > 팬츠 > 데님
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryPath[2].name").value("데님"));
    }

    // ── listing upsert ───────────────────────────

    @Test
    void 게시글_없던_상품에_listing을_보내면_생성되고_응답은_200이다() throws Exception {
        long id = 상품만_등록한다("미게시 상품");

        수정한다(id, """
                {"listing": {"title": "첫 게시", "description": "설명", "isSinglePieceAllowed": true,
                             "images": ["https://cdn.ondo.example/a.jpg"],
                             "variantPrices": [{"colorId": 1, "size": "S", "salePrice": 19000, "orderLimit": 0}]}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.listing.seasonStartedAt").exists())
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(19000));
    }

    @Test
    void 게시글_생성_분기에서_title이_없으면_400이다() throws Exception {
        long id = 상품만_등록한다("무제 상품");

        수정한다(id, """
                {"listing": {"variantPrices": [{"colorId": 1, "size": "S", "salePrice": 19000, "orderLimit": 0}]}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 기존_게시글의_제목과_설명을_고친다() throws Exception {
        long id = 게시글까지_등록한다("상품", "옛 제목");

        수정한다(id, "{\"listing\": {\"title\": \"새 제목\", \"description\": \"새 설명\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.title").value("새 제목"))
                .andExpect(jsonPath("$.data.listing.description").value("새 설명"))
                .andExpect(jsonPath("$.data.listing.images", hasSize(2))); // 이미지는 안 보냈으니 그대로
    }

    @Test
    void listing_내부_필드에_null을_보내면_무변경이다() throws Exception {
        long id = 게시글까지_등록한다("상품", "지킬 제목");

        수정한다(id, "{\"listing\": {\"title\": null, \"description\": \"새 설명\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.title").value("지킬 제목"))
                .andExpect(jsonPath("$.data.listing.description").value("새 설명"));
    }

    @Test
    void description을_빈_문자열로_보내면_지워진다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목");

        수정한다(id, "{\"listing\": {\"description\": \"\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.description").value((Object) null));
    }

    @Test
    void images는_전체_교체돼_sortOrder가_인덱스를_따른다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목");

        수정한다(id, "{\"listing\": {\"images\": [\"https://cdn.ondo.example/new1.jpg\", \"https://cdn.ondo.example/new2.jpg\", \"https://cdn.ondo.example/new3.jpg\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.images", hasSize(3)))
                .andExpect(jsonPath("$.data.listing.images[0].url").value("https://cdn.ondo.example/new1.jpg"))
                .andExpect(jsonPath("$.data.listing.images[0].sortOrder").value(0));

        em.flush(); // orphanRemoval DELETE 는 flush 에 나간다 — JdbcTemplate 은 flush 를 유발하지 않음
        Integer rows = jdbc.queryForObject("select count(*) from wholesale.listing_image", Integer.class);
        assertThat(rows).isEqualTo(3); // 기존 2장은 행까지 사라진다
    }

    @Test
    void images를_빈_배열로_보내면_전부_지워진다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목");

        수정한다(id, "{\"listing\": {\"images\": []}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.images", hasSize(0)));
    }

    // ── variantPrices ────────────────────────────

    @Test
    void variantPrices는_variantId로_기존_가격을_덮는다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목");
        long variantS = variantId(id, "S");
        long variantM = variantId(id, "M");

        수정한다(id, """
                {"listing": {"variantPrices": [
                    {"variantId": %d, "salePrice": 31000, "orderLimit": 10},
                    {"variantId": %d, "salePrice": 33000, "orderLimit": 0}]}}
                """.formatted(variantS, variantM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(31000))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].orderLimit").value(10))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[1].salePrice").value(33000));
    }

    @Test
    void 기존_variant를_색과_사이즈로_지정해도_된다() throws Exception {
        // 팀 결정(2026-09-04): 살아있는 variant 로 해석되면 지정 방식 무관 허용
        long id = 게시글까지_등록한다("상품", "제목");
        long variantM = variantId(id, "M");

        수정한다(id, """
                {"listing": {"variantPrices": [
                    {"colorId": 1, "size": "S", "salePrice": 25000, "orderLimit": 0},
                    {"variantId": %d, "salePrice": 33000, "orderLimit": 0}]}}
                """.formatted(variantM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(25000));
    }

    @Test
    void 같은_variant를_variantId와_색사이즈로_두_번_덮으면_400이다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목");
        long variantS = variantId(id, "S");

        수정한다(id, """
                {"listing": {"variantPrices": [
                    {"variantId": %d, "salePrice": 29000, "orderLimit": 0},
                    {"colorId": 1, "size": "S", "salePrice": 35000, "orderLimit": 0},
                    {"colorId": 1, "size": "M", "salePrice": 29000, "orderLimit": 0}]}}
                """.formatted(variantS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void variantPrices에_일부_variant가_빠지면_PRICE_REQUIRED다() throws Exception {
        long id = 게시글까지_등록한다("상품", "제목"); // S·M 두 variant
        long variantS = variantId(id, "S");

        수정한다(id, """
                {"listing": {"variantPrices": [{"variantId": %d, "salePrice": 29000, "orderLimit": 0}]}}
                """.formatted(variantS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));
    }

    @Test
    void 다른_상품의_variantId로_덮으려_하면_INVARIANT_VIOLATED다() throws Exception {
        long mine = 게시글까지_등록한다("내 상품", "내 게시");
        long other = 게시글까지_등록한다("남 상품", "남 게시");
        long othersVariant = variantId(other, "S");
        long myVariantM = variantId(mine, "M");

        수정한다(mine, """
                {"listing": {"variantPrices": [
                    {"variantId": %d, "salePrice": 1000, "orderLimit": 0},
                    {"variantId": %d, "salePrice": 29000, "orderLimit": 0}]}}
                """.formatted(othersVariant, myVariantM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATED"));
    }

    // ── colorOptions 전체 교체 diff (MUL-93 2단계) ──

    @Test
    void 색을_추가하면_variant_seq가_이어서_발급된다() throws Exception {
        long id = 상품만_등록한다("색 추가 상품"); // 블랙 S = seq 1

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"S\", \"M\"]}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions", hasSize(2)));

        em.flush();
        Integer maxSeq = jdbc.queryForObject(
                "select max(variant_seq) from wholesale.variant where product_id = ?", Integer.class, id);
        assertThat(maxSeq).isEqualTo(3); // 1(기존 S) + 2·3(베이지 S·M)
    }

    @Test
    void 사이즈를_지웠다_다시_추가하면_새_variant_행이_생기고_새_가격을_요구한다() throws Exception {
        long id = 게시글까지_등록한다("재추가 상품", "재추가 게시"); // S·M, 가격 29000
        long oldVariantM = variantId(id, "M");

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk()); // M 제거 (재고 없음)

        // M 재추가 — 가격을 안 보내면 새 variant 가 가격 없이 남으므로 400
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\", \"M\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));

        // 가격과 함께 재추가 — 새 행·새 seq, 옛 가격 행은 D-053 으로 남는다
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\", \"M\"]}],"
                + " \"listing\": {\"variantPrices\": ["
                + "   {\"colorId\": 1, \"size\": \"S\", \"salePrice\": 29000, \"orderLimit\": 0},"
                + "   {\"colorId\": 1, \"size\": \"M\", \"salePrice\": 31000, \"orderLimit\": 0}]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions[0].variants", hasSize(2)))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[1].salePrice").value(31000));

        em.flush();
        long newVariantM = 살아있는_variantId(id, "M");
        assertThat(newVariantM).isNotEqualTo(oldVariantM); // 새 행이다
        Integer priceRows = jdbc.queryForObject(
                "select count(*) from wholesale.listing_variant", Integer.class);
        assertThat(priceRows).isEqualTo(3); // S + 옛 M(잔존) + 새 M
    }

    @Test
    void 색을_빼면_옵션_행은_남고_variant만_soft_delete돼_상세에서_사라진다() throws Exception {
        long id = 상품만_등록한다("색 빼기 상품");
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk());

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions", hasSize(1)))
                .andExpect(jsonPath("$.data.colorOptions[0].color.name").value("블랙"));

        em.flush();
        Integer optionRows = jdbc.queryForObject(
                "select count(*) from wholesale.color_option where product_id = ?", Integer.class, id);
        assertThat(optionRows).isEqualTo(2); // 옵션 행은 안 지운다 (D-053)
        Integer aliveBeige = jdbc.queryForObject("select count(*) from wholesale.variant v"
                + " join wholesale.color_option co on co.id = v.color_option_id"
                + " where v.product_id = ? and co.color_id = 7 and v.deleted_at is null", Integer.class, id);
        assertThat(aliveBeige).isZero();
    }

    @Test
    void 뺐던_색을_다시_추가하면_옵션_행을_재사용한다() throws Exception {
        long id = 상품만_등록한다("색 재추가 상품");
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk());
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk()); // 베이지 제거 (옵션 행은 남음)

        // 재추가 — color_option_uk 때문에 새 행 INSERT 는 불가, 남은 행을 재사용해야 한다
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"M\"]}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions", hasSize(2)))
                .andExpect(jsonPath("$.data.colorOptions[1].color.name").value("베이지"));

        em.flush();
        Integer optionRows = jdbc.queryForObject(
                "select count(*) from wholesale.color_option where product_id = ?", Integer.class, id);
        assertThat(optionRows).isEqualTo(2); // 행 수 그대로 = 재사용
    }

    @Test
    void 재고_있는_사이즈는_뺄_수_없다_409() throws Exception {
        long id = 게시글까지_등록한다("재고 있는 상품", "재고 게시");
        long variantM = variantId(id, "M");
        jdbc.update("update wholesale.variant set stock_qty = 5 where id = ?", variantM);
        em.clear();

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_HAS_STOCK"));
    }

    @Test
    void 일부만_409에_걸려도_아무것도_안_지워진다() throws Exception {
        long id = 상품만_등록한다("원자성 상품"); // 블랙 S
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk());
        long beigeVariant = jdbc.queryForObject("select v.id from wholesale.variant v"
                + " join wholesale.color_option co on co.id = v.color_option_id"
                + " where v.product_id = ? and co.color_id = 7", Long.class, id);
        jdbc.update("update wholesale.variant set stock_qty = 5 where id = ?", beigeVariant);
        em.clear();

        // 블랙(재고 없음)과 베이지(재고 있음)를 같이 빼려 한다 → 409, 블랙도 살아있어야 한다
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 11, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isConflict());

        Integer alive = jdbc.queryForObject(
                "select count(*) from wholesale.variant where product_id = ? and deleted_at is null",
                Integer.class, id);
        assertThat(alive).isEqualTo(2); // 아무것도 안 지워졌다
    }

    @Test
    void 유지되는_variant에_재고가_있어도_빼는_것만_검사한다() throws Exception {
        long id = 상품만_등록한다("유지 상품");
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 7, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk());
        long blackVariant = jdbc.queryForObject("select v.id from wholesale.variant v"
                + " join wholesale.color_option co on co.id = v.color_option_id"
                + " where v.product_id = ? and co.color_id = 1 and v.deleted_at is null", Long.class, id); // 블랙 S
        jdbc.update("update wholesale.variant set stock_qty = 5 where id = ?", blackVariant);
        em.clear();

        // 재고 있는 블랙은 유지, 재고 없는 베이지만 제거 → 성공해야 한다
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions", hasSize(1)));
    }

    @Test
    void 색을_빼도_남은_variant_가격은_유지되고_옛_가격_행도_남는다() throws Exception {
        long id = 게시글까지_등록한다("가격 유지 상품", "게시"); // S·M 29000

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk()) // M 제거 — 가격 안 보내도 남은 S 는 기존 행으로 커버됨
                .andExpect(jsonPath("$.data.colorOptions[0].variants", hasSize(1)))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000));

        em.flush();
        Integer priceRows = jdbc.queryForObject(
                "select count(*) from wholesale.listing_variant", Integer.class);
        assertThat(priceRows).isEqualTo(2); // M 의 가격 행도 지우지 않는다 (D-053)
    }

    @Test
    void colorOptions를_빈_배열로_보내면_OPTION_REQUIRED다() throws Exception {
        long id = 상품만_등록한다("빈 옵션 상품");
        수정한다(id, "{\"colorOptions\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPTION_REQUIRED"));
    }

    @Test
    void PATCH에서도_색이_중복되면_COLOR_DUPLICATED다() throws Exception {
        long id = 상품만_등록한다("중복 색 상품");
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}, {\"colorId\": 1, \"sizes\": [\"M\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COLOR_DUPLICATED"));
    }

    @Test
    void colorOptions_교체_후_신규_variant는_색과_사이즈로_가격을_받는다() throws Exception {
        long id = 게시글까지_등록한다("확장 상품", "게시"); // 블랙 S·M

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\", \"M\"]}, {\"colorId\": 7, \"sizes\": [\"S\"]}],"
                + " \"listing\": {\"variantPrices\": ["
                + "   {\"colorId\": 1, \"size\": \"S\", \"salePrice\": 29000, \"orderLimit\": 0},"
                + "   {\"colorId\": 1, \"size\": \"M\", \"salePrice\": 29000, \"orderLimit\": 0},"
                + "   {\"colorId\": 7, \"size\": \"S\", \"salePrice\": 27000, \"orderLimit\": 0}]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorOptions", hasSize(2)))
                .andExpect(jsonPath("$.data.colorOptions[1].variants[0].salePrice").value(27000));
    }

    @Test
    void 게시_상품에서_variant를_추가하며_가격을_안_보내면_PRICE_REQUIRED다() throws Exception {
        long id = 게시글까지_등록한다("가격 누락 상품", "게시");

        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\", \"M\", \"XL\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));
    }

    @Test
    void soft_delete된_variant의_variantId로_가격을_덮으려_하면_INVARIANT_VIOLATED다() throws Exception {
        long id = 게시글까지_등록한다("유령 상품", "게시");
        long variantM = variantId(id, "M");
        수정한다(id, "{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}]}")
                .andExpect(status().isOk()); // M soft delete

        수정한다(id, "{\"listing\": {\"variantPrices\": ["
                + "   {\"colorId\": 1, \"size\": \"S\", \"salePrice\": 29000, \"orderLimit\": 0},"
                + "   {\"variantId\": " + variantM + ", \"salePrice\": 1000, \"orderLimit\": 0}]}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATED"));
    }

    // ── 스코핑 ───────────────────────────────────

    @Test
    void 타_도매처_상품_PATCH는_404다() throws Exception {
        long id = 상품만_등록한다("내 상품");
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other-u@ondo.test", "9500000002");

        mvc.perform(patch("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"탈취 시도\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_상품_PATCH는_404다() throws Exception {
        long id = 상품만_등록한다("지운 상품");
        jdbc.update("update wholesale.product set deleted_at = now() where id = ?", id);

        수정한다(id, "{\"name\": \"부활 시도\"}")
                .andExpect(status().isNotFound());
    }

    // ── 헬퍼 ─────────────────────────────────────

    private ResultActions 수정한다(long id, String body) throws Exception {
        return mvc.perform(patch("/api/wholesale/products/" + id)
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long 상품만_등록한다(String name) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }

    /** 블랙 S·M + 게시글(이미지 2장, 판매가 29000) 으로 등록하고 상품 id 를 돌려준다. */
    private long 게시글까지_등록한다(String name, String title) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "categoryId": 121,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S", "M"]}],
                                 "listing": {"title": "%s", "description": "설명", "isSinglePieceAllowed": false,
                                             "images": ["https://cdn.ondo.example/1.jpg", "https://cdn.ondo.example/2.jpg"],
                                             "variantPrices": [
                                                 {"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0},
                                                 {"colorId": 1, "size": "M", "salePrice": 29000, "orderLimit": 0}]}}
                                """.formatted(name, title)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }

    private long variantId(long productId, String size) {
        return jdbc.queryForObject(
                "select id from wholesale.variant where product_id = ? and size = ?", Long.class, productId, size);
    }

    /** 살아있는 행만 — 재추가 뒤 새 행을 집는다. */
    private long 살아있는_variantId(long productId, String size) {
        return jdbc.queryForObject(
                "select id from wholesale.variant where product_id = ? and size = ? and deleted_at is null",
                Long.class, productId, size);
    }
}
