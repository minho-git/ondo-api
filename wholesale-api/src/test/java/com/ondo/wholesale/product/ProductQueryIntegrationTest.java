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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 목록·단건 조회 흐름 검증 (MUL-92). 등록 API 로 데이터를 만들고 조회로 확인한다 —
 * V5 시드 카테고리(여성>상의>티셔츠 121 / 여성>팬츠>데님 162) 위에서 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductQueryIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long wholesalerId;

    @BeforeEach
    void 도매처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "query@ondo.test", "9400000001");
    }

    @Test
    void 목록_기본_정렬은_createdAt_desc다() throws Exception {
        상품을_등록한다("먼저 등록", 121);
        상품을_등록한다("나중 등록", 121);

        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("나중 등록"))
                .andExpect(jsonPath("$.data[1].name").value("먼저 등록"))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.page").value(0));
    }

    @Test
    void q는_품명과_게시_제목_양쪽을_찾는다() throws Exception {
        상품을_등록한다("무지 티셔츠", 121);
        게시글까지_등록한다("살구색 상품", 121, "[신상] 오버핏 티셔츠 남방");
        상품을_등록한다("바지", 162);

        // 품명 매치 1건 + 게시 제목 매치 1건
        mvc.perform(get("/api/wholesale/products?q=티셔츠").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(2)));
        // 대소문자 무시 검색은 아니고 부분 일치 — 게시 제목만 매치
        mvc.perform(get("/api/wholesale/products?q=남방").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("살구색 상품"));
    }

    @Test
    void 상위_카테고리로_거르면_하위_상품이_함께_나온다() throws Exception {
        상품을_등록한다("티셔츠 상품", 121);  // 여성 > 상의 > 티셔츠
        상품을_등록한다("데님 상품", 162);    // 여성 > 팬츠 > 데님

        // 여성(1) 전체
        mvc.perform(get("/api/wholesale/products?categoryId=1").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(2)));
        // 상의(12) 하위만
        mvc.perform(get("/api/wholesale/products?categoryId=12").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("티셔츠 상품"))
                .andExpect(jsonPath("$.data[0].categoryPath[2].name").value("티셔츠"));
    }

    @Test
    void 기간_필터는_등록일_기준이다() throws Exception {
        상품을_등록한다("오늘 상품", 121);
        // created_at 을 과거로 밀어 과거 등록을 흉내낸다
        jdbc.update("update wholesale.product set created_at = created_at - interval '10 days' where name = '오늘 상품'");
        상품을_등록한다("방금 상품", 121);

        mvc.perform(get("/api/wholesale/products?from=" + java.time.LocalDate.now().minusDays(1))
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("방금 상품"));
    }

    @Test
    void 기간_양끝을_주면_그_사이_등록만_나온다() throws Exception {
        상품을_등록한다("과거 상품", 121);
        상품을_등록한다("중간 상품", 121);
        상품을_등록한다("오늘 상품", 121);
        jdbc.update("update wholesale.product set created_at = created_at - interval '10 days' where name = '과거 상품'");
        jdbc.update("update wholesale.product set created_at = created_at - interval '5 days' where name = '중간 상품'");

        java.time.LocalDate today = java.time.LocalDate.now();
        mvc.perform(get("/api/wholesale/products?from=" + today.minusDays(7) + "&to=" + today.minusDays(3))
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("중간 상품"));

        // to 만 주면 그 날짜까지 전부
        mvc.perform(get("/api/wholesale/products?to=" + today.minusDays(3))
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void sort는_방향을_생략하면_오름차순이고_asc_명시와_같다() throws Exception {
        상품을_등록한다("나 상품", 121);
        상품을_등록한다("가 상품", 121);

        mvc.perform(get("/api/wholesale/products?sort=name").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].name").value("가 상품"));
        mvc.perform(get("/api/wholesale/products?sort=name,asc").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].name").value("가 상품"));
    }

    @Test
    void colorCount와_variantCount는_삭제된_variant를_빼고_센다() throws Exception {
        long id = 게시글까지_등록한다("집계 상품", 121, "집계 게시");
        // 블랙 S 를 soft delete 한 상황을 흉내낸다
        jdbc.update("""
                update wholesale.variant set deleted_at = now()
                 where product_id = ? and size = 'M'
                """, id);

        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].colorCount").value(1))
                .andExpect(jsonPath("$.data[0].variantCount").value(1))
                .andExpect(jsonPath("$.data[0].listingStatus").value("ON_SALE"));
    }

    @Test
    void 게시글_없는_상품은_listingStatus가_null이다() throws Exception {
        상품을_등록한다("미게시 상품", 121);
        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].listingStatus").value((Object) null));
    }

    @Test
    void 타_도매처_상품은_목록에_안_보인다() throws Exception {
        상품을_등록한다("내 상품", 121);
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other-q@ondo.test", "9400000002");

        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(other)))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void 상세는_등록_응답과_같은_스키마로_내려온다() throws Exception {
        long id = 게시글까지_등록한다("상세 상품", 121, "상세 게시");

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("상세 상품"))
                .andExpect(jsonPath("$.data.categoryPath[0].name").value("여성"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].size").value("S"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000))
                .andExpect(jsonPath("$.data.listing.title").value("상세 게시"));
    }

    @Test
    void availableQty는_stockQty에서_allocatedQty를_뺀_값이다() throws Exception {
        long id = 상품을_등록한다("재고 상품", 121);
        jdbc.update("update wholesale.variant set stock_qty = 10, reserved_qty = 3 where product_id = ?", id);
        em.clear(); // jdbc 로 고친 값이 1차 캐시에 가려지지 않게

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].stockQty").value(10))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].allocatedQty").value(3))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].availableQty").value(7));
    }

    @Test
    void 미송이_걸린_variant는_상세에_backorderQty가_내려온다() throws Exception {
        long id = 게시글까지_등록한다("미송 상품", 121, "미송 게시"); // 블랙 S·M
        long variantS = jdbc.queryForObject(
                "select id from wholesale.variant where product_id = ? and size = 'S'", Long.class, id);
        long partnerId = jdbc.queryForObject(
                "insert into wholesale.partner (wholesaler_id, retailer_id, retailer_name) values (?, 1, '소매상') returning id",
                Long.class, wholesalerId);
        long orderId = jdbc.queryForObject(
                "insert into wholesale.orders (order_number, partner_id, wholesaler_id, status, payment_term, receive_method, ordered_at)"
                        + " values (1, ?, ?, 'CONFIRMED', 'CASH', 'PICKUP', now()) returning id",
                Long.class, partnerId, wholesalerId);
        long orderItemId = jdbc.queryForObject(
                "insert into wholesale.order_item (order_id, variant_id, qty, unit_price) values (?, ?, 4, 29000) returning id",
                Long.class, orderId, variantS);
        jdbc.update("insert into wholesale.backorder (order_item_id, qty, status) values (?, 4, 'OPEN')", orderItemId);

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].backorderQty").value(4))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[1].backorderQty").value(0))
                // 미송은 availableQty 에서 빼지 않는다 (계약)
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].availableQty").value(0));
    }

    @Test
    void 타_도매처_상품_상세는_404다() throws Exception {
        long id = 상품을_등록한다("내 상품", 121);
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other-d@ondo.test", "9400000003");

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 삭제된_상품은_404다() throws Exception {
        long id = 상품을_등록한다("지운 상품", 121);
        jdbc.update("update wholesale.product set deleted_at = now() where id = ?", id);

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound());
    }

    private long 상품을_등록한다(String name, long categoryId) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"categoryId\": " + categoryId
                                + ", \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }

    /** 블랙 S·M 두 variant + 게시글(판매가 29000) 로 등록하고 상품 id 를 돌려준다. */
    private long 게시글까지_등록한다(String name, long categoryId, String title) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "categoryId": %d,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S", "M"]}],
                                 "listing": {"title": "%s", "description": null, "isSinglePieceAllowed": false,
                                             "images": [],
                                             "variantPrices": [
                                                 {"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0},
                                                 {"colorId": 1, "size": "M", "salePrice": 29000, "orderLimit": 0}]}}
                                """.formatted(name, categoryId, title)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }
}
