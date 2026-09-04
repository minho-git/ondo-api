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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시글 시즌 전이 흐름 검증 (MUL-94). ON_SALE ↔ SEASON_ENDED 둘뿐이고,
 * 시즌을 끝내도 상품·재고·판매가는 전부 남는다 — 마켓 노출만 내려간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingSeasonIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long wholesalerId;
    private long productId;
    private long listingId;

    @BeforeEach
    void 게시_상품을_심는다() throws Exception {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "season@ondo.test", "9800000001");
        mvc.perform(post("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "시즌 상품", "categoryId": 121,
                                 "colorOptions": [{"colorId": 1, "sizes": ["S"]}],
                                 "listing": {"title": "시즌 게시", "description": null, "isSinglePieceAllowed": false,
                                             "images": ["https://cdn.ondo.example/1.jpg", "https://cdn.ondo.example/2.jpg"],
                                             "variantPrices": [{"colorId": 1, "size": "S", "salePrice": 29000, "orderLimit": 0}]}}
                                """))
                .andExpect(status().isCreated());
        productId = jdbc.queryForObject("select id from wholesale.product where name = '시즌 상품'", Long.class);
        listingId = jdbc.queryForObject("select id from wholesale.listing where product_id = ?", Long.class, productId);
    }

    @Test
    void 시즌을_끝내면_SEASON_ENDED와_종료_시각이_내려온다() throws Exception {
        시즌을_끝낸다(listingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEASON_ENDED"))
                .andExpect(jsonPath("$.data.seasonEndedAt").exists())
                .andExpect(jsonPath("$.data.title").value("시즌 게시"))
                // 응답은 ListingResponse 전체 스키마 — 이미지까지 정렬돼 담긴다
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0));
    }

    @Test
    void 이미_끝난_시즌을_또_끝내면_409다() throws Exception {
        시즌을_끝낸다(listingId).andExpect(status().isOk());

        시즌을_끝낸다(listingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void 재개하면_판매중으로_돌아오고_시작_시각이_갱신된다() throws Exception {
        OffsetDateTime 처음_시작 = 시작_시각();
        시즌을_끝낸다(listingId).andExpect(status().isOk());

        재개한다(listingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.seasonEndedAt").value((Object) null));

        assertThat(시작_시각()).isAfter(처음_시작); // 재개 시각으로 갱신
    }

    @Test
    void 이미_판매중이면_재개는_409다() throws Exception {
        재개한다(listingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void 끝내고_재개하고_다시_끝낼_수_있다() throws Exception {
        시즌을_끝낸다(listingId).andExpect(status().isOk());
        OffsetDateTime 첫_종료 = 종료_시각();

        재개한다(listingId).andExpect(status().isOk());
        시즌을_끝낸다(listingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEASON_ENDED"));

        assertThat(종료_시각()).isAfter(첫_종료); // 두 번째 종료 시각으로 다시 찍힌다
    }

    @Test
    void 시즌을_끝내도_재고와_가격은_그대로다() throws Exception {
        jdbc.update("update wholesale.variant set stock_qty = 7 where product_id = ?", productId);
        em.clear(); // jdbc 로 고친 값이 1차 캐시에 가려지지 않게
        시즌을_끝낸다(listingId).andExpect(status().isOk());

        mvc.perform(get("/api/wholesale/products/" + productId).with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.listing.status").value("SEASON_ENDED"))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].stockQty").value(7))
                .andExpect(jsonPath("$.data.colorOptions[0].variants[0].salePrice").value(29000));
    }

    @Test
    void 시즌_끝난_게시글도_수정할_수_있다() throws Exception {
        시즌을_끝낸다(listingId).andExpect(status().isOk());

        mvc.perform(patch("/api/wholesale/products/" + productId).with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listing\": {\"title\": \"내려둔 채 고친 제목\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.title").value("내려둔 채 고친 제목"))
                .andExpect(jsonPath("$.data.listing.status").value("SEASON_ENDED")); // 상태는 그대로
    }

    @Test
    void 시즌을_끝내면_목록_listingStatus가_SEASON_ENDED다() throws Exception {
        시즌을_끝낸다(listingId).andExpect(status().isOk());

        mvc.perform(get("/api/wholesale/products").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].listingStatus").value("SEASON_ENDED"));
    }

    @Test
    void 타_도매처_게시글은_404다() throws Exception {
        long other = MasterDataFixture.도매처를_넣는다(jdbc, "other-season@ondo.test", "9800000002");

        mvc.perform(post("/api/wholesale/listings/" + listingId + "/season-end")
                        .with(TestSecuritySupport.approvedAs(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 없는_게시글은_404다() throws Exception {
        시즌을_끝낸다(999999L).andExpect(status().isNotFound());
    }

    // ── 헬퍼 ─────────────────────────────────────

    private ResultActions 시즌을_끝낸다(long id) throws Exception {
        return mvc.perform(post("/api/wholesale/listings/" + id + "/season-end")
                .with(TestSecuritySupport.approvedAs(wholesalerId)));
    }

    private ResultActions 재개한다(long id) throws Exception {
        return mvc.perform(post("/api/wholesale/listings/" + id + "/reopen")
                .with(TestSecuritySupport.approvedAs(wholesalerId)));
    }

    private OffsetDateTime 시작_시각() {
        em.flush(); // 전이 결과를 DB 에 반영한 뒤 읽는다
        return jdbc.queryForObject(
                "select season_started_at from wholesale.listing where id = ?", OffsetDateTime.class, listingId);
    }

    private OffsetDateTime 종료_시각() {
        em.flush(); // 전이 결과를 DB 에 반영한 뒤 읽는다
        return jdbc.queryForObject(
                "select season_ended_at from wholesale.listing where id = ?", OffsetDateTime.class, listingId);
    }
}
