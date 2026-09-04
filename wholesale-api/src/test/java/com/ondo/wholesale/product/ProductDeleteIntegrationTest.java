package com.ondo.wholesale.product;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 삭제 흐름 검증 (MUL-94). 삭제는 soft delete 만 — 행은 남고 품번은 영구 결번(D-004).
 * 게시글도 함께 지워진다 ("게시글 삭제" 버튼이 곧 이 호출이다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductDeleteIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long wholesalerId;

    @BeforeEach
    void 도매처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "delete@ondo.test", "9700000001");
    }

    @Test
    void 삭제하면_204_빈_본문이고_목록과_상세에서_사라진다() throws Exception {
        long id = 게시글까지_등록한다("지울 상품", "지울 게시");

        삭제한다(id)
                .andExpect(status().isNoContent())
                .andExpect(content().string("")); // 봉투 없이 빈 본문

        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 행은_남고_deleted_at만_찍힌다() throws Exception {
        long id = 게시글까지_등록한다("잔존 상품", "잔존 게시"); // 2색 3variant

        삭제한다(id).andExpect(status().isNoContent());
        em.flush();

        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.product where id = ? and deleted_at is not null",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.listing where product_id = ? and deleted_at is not null",
                Integer.class, id)).isEqualTo(1);
        // 살아있던 variant 3개 전부 soft delete — 행은 그대로
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.variant where product_id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.variant where product_id = ? and deleted_at is null",
                Integer.class, id)).isZero();
        // 가격 행도 지우지 않는다 (D-053)
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.listing_variant", Integer.class)).isEqualTo(3);
    }

    @Test
    void 게시글_없는_상품도_지워진다() throws Exception {
        long id = 상품만_등록한다("미게시 상품");
        삭제한다(id).andExpect(status().isNoContent());

        mvc.perform(get("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 사이즈_뺀_이력이_있어도_지워지고_옛_삭제_시각은_안_바뀐다() throws Exception {
        long id = 상품만_등록한다("이력 상품"); // 블랙 S
        mvc.perform(patch("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"M\"]}]}"))
                .andExpect(status().isOk()); // S 는 이때 soft delete
        em.flush();
        OffsetDateTime firstDeletedAt = jdbc.queryForObject(
                "select deleted_at from wholesale.variant where product_id = ? and size = 'S'",
                OffsetDateTime.class, id);

        삭제한다(id).andExpect(status().isNoContent());
        em.flush();

        // 죽어 있던 S 의 삭제 시각은 그대로 — 삭제 대상은 살아있는 variant 뿐이다
        OffsetDateTime afterDeletedAt = jdbc.queryForObject(
                "select deleted_at from wholesale.variant where product_id = ? and size = 'S'",
                OffsetDateTime.class, id);
        assertThat(afterDeletedAt).isEqualTo(firstDeletedAt);
    }

    @Test
    void 삭제_후_새로_등록하면_품번이_결번을_건너뛴다() throws Exception {
        long first = 상품만_등록한다("결번 상품"); // 품번 1
        삭제한다(first).andExpect(status().isNoContent());

        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"다음 상품\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productNumber").value(2)); // 1 은 영구 결번
    }

    @Test
    void 재고가_있으면_409다() throws Exception {
        long id = 상품만_등록한다("재고 상품");
        jdbc.update("update wholesale.variant set stock_qty = 5 where product_id = ?", id);
        em.clear();

        삭제한다(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_HAS_STOCK"));
    }

    @Test
    void 처리중_주문에_잡혀있으면_409고_아무것도_안_지워진다() throws Exception {
        long id = 게시글까지_등록한다("주문 상품", "주문 게시");
        long variantS = jdbc.queryForObject("select v.id from wholesale.variant v"
                + " join wholesale.color_option co on co.id = v.color_option_id"
                + " where v.product_id = ? and co.color_id = 1 and v.size = 'S'", Long.class, id); // 블랙 S
        long partnerId = jdbc.queryForObject(
                "insert into wholesale.partner (wholesaler_id, retailer_id, retailer_name) values (?, 1, '소매상') returning id",
                Long.class, wholesalerId);
        long orderId = jdbc.queryForObject(
                "insert into wholesale.orders (order_number, partner_id, wholesaler_id, status, payment_term, receive_method, ordered_at)"
                        + " values (1, ?, ?, 'NEW', 'CASH', 'PICKUP', now()) returning id",
                Long.class, partnerId, wholesalerId);
        jdbc.update("insert into wholesale.order_item (order_id, variant_id, qty, unit_price) values (?, ?, 1, 29000)",
                orderId, variantS);

        삭제한다(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_IN_PENDING_ORDER"));

        em.flush();
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.product where id = ? and deleted_at is null", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.listing where product_id = ? and deleted_at is null", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.variant where product_id = ? and deleted_at is null", Integer.class, id)).isEqualTo(3);
    }

    @Test
    void 타_도매처와_이미_삭제된_상품은_404다() throws Exception {
        long id = 상품만_등록한다("내 상품");
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other-del@ondo.test", "9700000002");

        mvc.perform(delete("/api/wholesale/products/" + id).with(TestSecuritySupport.approvedAs(other)))
                .andExpect(status().isNotFound());

        삭제한다(id).andExpect(status().isNoContent());
        삭제한다(id).andExpect(status().isNotFound()); // 재삭제
    }

    // ── 헬퍼 ─────────────────────────────────────

    private ResultActions 삭제한다(long id) throws Exception {
        return mvc.perform(delete("/api/wholesale/products/" + id)
                .with(TestSecuritySupport.approvedAs(wholesalerId)));
    }

    private long 상품만_등록한다(String name) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"categoryId\": 121, \"colorOptions\": [{\"colorId\": 1, \"sizes\": [\"S\"]}], \"listing\": null}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }

    /** 2색(블랙 S·M + 베이지 S) 3variant + 게시글·가격으로 등록. */
    private long 게시글까지_등록한다(String name, String title) throws Exception {
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "categoryId": 121,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S", "M"]}, {"colorId": 7, "sizes": ["S"]}],
                                 "listing": {"title": "%s", "description": null, "isSinglePieceAllowed": false, "images": [],
                                             "variantPrices": [
                                                 {"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0},
                                                 {"colorId": 1, "size": "M", "salePrice": 29000, "orderLimit": 0},
                                                 {"colorId": 7, "size": "S", "salePrice": 27000, "orderLimit": 0}]}}
                                """.formatted(name, title)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from wholesale.product where name = ?", Long.class, name);
    }
}
