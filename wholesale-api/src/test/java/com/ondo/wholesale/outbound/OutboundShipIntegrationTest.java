package com.ondo.wholesale.outbound;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.OutboundFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 출고 확정·장끼 API 통합 검증 (MUL-49) — 재고가 실제로 줄어드는 유일한 지점.
 *
 * <p>바탕 픽스처 — 봉투 OB1(가나상회): 포장 P1(주문A 니트 3) + P2(주문B 니트 2 · 셔츠 4).
 * 니트가 두 포장에 갈라져 있어 SKU 합산 차감의 재료다. 재고: 니트 10/예약 7 ·
 * 셔츠 8/예약 4. 금액: 주문A 3×1000 · 주문B 2×1000+4×2000.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutboundShipIntegrationTest extends PostgresTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long 가나상회;
    private long 니트;
    private long 셔츠;
    private long batchId;
    private long 출고1;
    private long 주문A;
    private long 니트라인A;
    private long 주문B;
    private long 니트라인B;
    private long 셔츠라인B;

    @BeforeEach
    void 봉투를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-ship@ondo.test", "9500000041");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9160);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9260, 9261);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "셔츠", 2);
        jdbc.update("update wholesale.variant set stock_qty = 10, reserved_qty = 7 where id = ?", 니트);
        jdbc.update("update wholesale.variant set stock_qty = 8, reserved_qty = 4 where id = ?", 셔츠);
        가나상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 751L, "가나상회");
        batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);

        출고1 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 1);
        주문A = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 1, "RETAILER", OffsetDateTime.now());
        니트라인A = OrderFixture.라인을_넣는다(jdbc, 주문A, 니트, 5, 1000, 3, 0);
        long 포장1 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문A, 출고1);
        OrderFixture.포장항목을_넣는다(jdbc, 포장1, 니트라인A, null, batchId, 3, false);

        주문B = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 2, "RETAILER", OffsetDateTime.now());
        니트라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 니트, 2, 1000, 2, 0);
        셔츠라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 셔츠, 4, 2000, 4, 0);
        long 포장2 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문B, 출고1);
        OrderFixture.포장항목을_넣는다(jdbc, 포장2, 니트라인B, null, batchId, 2, false);
        OrderFixture.포장항목을_넣는다(jdbc, 포장2, 셔츠라인B, null, batchId, 4, false);
    }

    @Test
    void 확정은_실재고와_예약을_함께_줄인다() throws Exception {
        mvc.perform(post("/api/wholesale/outbounds/" + 출고1 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(출고1))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.statementNumber").value(1))
                .andExpect(jsonPath("$.data.isShippable").value(false))
                .andExpect(jsonPath("$.data.totalQty").value(9));

        assertThat(정수("select stock_qty from wholesale.variant where id = " + 니트)).isEqualTo(5);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(2);
        assertThat(정수("select stock_qty from wholesale.variant where id = " + 셔츠)).isEqualTo(4);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 셔츠)).isEqualTo(0);
    }

    @Test
    void variant당_OUT원장_한줄이_qtyAfter와_함께_남는다() throws Exception {
        확정한다(출고1);

        assertThat(정수("select count(*) from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo(1);
        assertThat(정수("select qty_change from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo(-5);
        assertThat(정수("select qty_after from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo(5);
        assertThat(문자열("select type from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo("OUT");
        assertThat(문자열("select ref_type from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo("OUTBOUND");
        assertThat(정수("select ref_id from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo((int) 출고1);
        assertThat(정수("select qty_change from wholesale.stock_movement where variant_id = " + 셔츠)).isEqualTo(-4);
        assertThat(정수("select qty_after from wholesale.stock_movement where variant_id = " + 셔츠)).isEqualTo(4);
    }

    @Test
    void 라인_shippedQty가_올라_주문파생상태가_바뀐다() throws Exception {
        확정한다(출고1);

        assertThat(정수("select shipped_qty from wholesale.order_item where id = " + 니트라인A)).isEqualTo(3);
        assertThat(정수("select shipped_qty from wholesale.order_item where id = " + 니트라인B)).isEqualTo(2);
        assertThat(정수("select shipped_qty from wholesale.order_item where id = " + 셔츠라인B)).isEqualTo(4);
        // 주문A 는 5 중 3 출고 = 부분 출고, 주문B 는 전량 출고 — 파생 상태 칩이 갈린다
        mvc.perform(get("/api/wholesale/orders").param("filter", "PARTIALLY_SHIPPED")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(주문A));
        mvc.perform(get("/api/wholesale/orders").param("filter", "SHIPPED")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(주문B));
    }

    @Test
    void 같은SKU_두포장은_합산해_한번_차감한다() throws Exception {
        확정한다(출고1);

        // 니트는 P1(3)·P2(2)에 갈라져 있지만 원장은 합산 한 줄, 차감도 한 번이다
        assertThat(정수("select count(*) from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo(1);
        assertThat(정수("select qty_change from wholesale.stock_movement where variant_id = " + 니트)).isEqualTo(-5);
        assertThat(정수("select stock_qty from wholesale.variant where id = " + 니트)).isEqualTo(5);
    }

    @Test
    void 재고부족이면_409로_전건_롤백된다() throws Exception {
        // 조정이 예약을 침식한 상황 — isShippable 이 참이어도 확정은 실패할 수 있다
        jdbc.update("update wholesale.variant set stock_qty = 4 where id = ?", 니트);

        mvc.perform(post("/api/wholesale/outbounds/" + 출고1 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        // 전건 롤백 — 셔츠 쪽도 원장·차감·라인·장끼·미수 전부 없던 일이 된다
        assertThat(정수("select stock_qty from wholesale.variant where id = " + 셔츠)).isEqualTo(8);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 셔츠)).isEqualTo(4);
        assertThat(정수("select count(*) from wholesale.stock_movement")).isZero();
        assertThat(정수("select shipped_qty from wholesale.order_item where id = " + 셔츠라인B)).isZero();
        assertThat(문자열("select shipped_at from wholesale.outbound where id = " + 출고1)).isNull();
        assertThat(정수("select count(*) from wholesale.receivable_ledger")).isZero();
        assertThat(정수("select last_statement_seq from wholesale.wholesaler where id = " + wholesalerId)).isZero();
    }

    @Test
    void 이미확정은_409_TRANSITION_NOT_ALLOWED다() throws Exception {
        확정한다(출고1);

        mvc.perform(post("/api/wholesale/outbounds/" + 출고1 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void 빈출고는_409_OUTBOUND_EMPTY다() throws Exception {
        jdbc.update("""
                update wholesale.packing_item set deleted_at = now()
                where packing_id in (select id from wholesale.packing where outbound_id = ?)
                """, 출고1);

        mvc.perform(post("/api/wholesale/outbounds/" + 출고1 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOUND_EMPTY"));
    }

    @Test
    void 장끼번호는_같은날_이어서_다음날_1부터다() throws Exception {
        // 오늘 이미 3번까지 발행한 도매처 — 이어서 4번
        jdbc.update("update wholesale.wholesaler set last_statement_date = ?, last_statement_seq = 3 where id = ?",
                LocalDate.now(KST), wholesalerId);
        mvc.perform(post("/api/wholesale/outbounds/" + 출고1 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNumber").value(4));

        // 날짜가 넘어간 두 번째 봉투 — 1부터 다시 (어제 발행분으로 되돌려 하루 경과를 흉내)
        long 주문F = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 3, "RETAILER", OffsetDateTime.now());
        long 니트라인F = OrderFixture.라인을_넣는다(jdbc, 주문F, 니트, 1, 1000, 1, 0);
        long 출고2 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 2);
        long 포장F = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문F, 출고2);
        OrderFixture.포장항목을_넣는다(jdbc, 포장F, 니트라인F, null, batchId, 1, false);
        jdbc.update("update wholesale.wholesaler set last_statement_date = ? where id = ?",
                LocalDate.now(KST).minusDays(1), wholesalerId);

        mvc.perform(post("/api/wholesale/outbounds/" + 출고2 + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNumber").value(1));
    }

    @Test
    void 확정전_장끼는_404다() throws Exception {
        mvc.perform(get("/api/wholesale/outbounds/" + 출고1 + "/statement")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 장끼는_판매자상호와_SKU합산_품목을_내린다() throws Exception {
        확정한다(출고1);

        mvc.perform(get("/api/wholesale/outbounds/" + 출고1 + "/statement")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNumber").value(1))
                .andExpect(jsonPath("$.data.outboundNumber").value(1))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sellerName").value("테스트도매"))
                .andExpect(jsonPath("$.data.retailerName").value("가나상회"))
                .andExpect(jsonPath("$.data.receiveBy").value("RETAILER"))
                .andExpect(jsonPath("$.data.totalQty").value(9))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productName").value("니트"))
                .andExpect(jsonPath("$.data.items[0].color").value("블랙"))
                .andExpect(jsonPath("$.data.items[0].size").value("FREE"))
                .andExpect(jsonPath("$.data.items[0].qty").value(5))
                .andExpect(jsonPath("$.data.items[1].productName").value("셔츠"))
                .andExpect(jsonPath("$.data.items[1].qty").value(4));
    }

    @Test
    void 확정은_미수_원장에_출고_행을_남긴다() throws Exception {
        확정한다(출고1);

        // 스키마상 모든 행이 주문을 가리킨다 — 봉투에 주문이 둘이면 주문별로 한 행씩 남는다
        assertThat(정수("select count(*) from wholesale.receivable_ledger")).isEqualTo(2);
        assertThat(정수("select delta from wholesale.receivable_ledger where order_id = " + 주문A)).isEqualTo(3000);
        assertThat(정수("select delta from wholesale.receivable_ledger where order_id = " + 주문B)).isEqualTo(10000);
        assertThat(정수("select sum(delta) from wholesale.receivable_ledger where partner_id = " + 가나상회))
                .isEqualTo(13000);
        assertThat(정수("select max(balance_after) from wholesale.receivable_ledger")).isEqualTo(13000);
        // 거래처 미수 칸은 원장 마지막 잔액을 베낀 값이다 — 미수 목록이 이 칸을 읽는다 (MUL-123)
        assertThat(정수("select receivable_balance from wholesale.partner where id = " + 가나상회)).isEqualTo(13000);
        assertThat(정수("select count(distinct entry_type) from wholesale.receivable_ledger")).isEqualTo(1);
        assertThat(문자열("select min(entry_type) from wholesale.receivable_ledger")).isEqualTo("OUTBOUND");
        assertThat(정수("select count(*) from wholesale.receivable_ledger where outbound_id = " + 출고1)).isEqualTo(2);
        // 미수 발생 시점 = 출고 시점 [X-1]
        assertThat(정수("""
                select count(*) from wholesale.receivable_ledger l
                join wholesale.outbound o on o.id = l.outbound_id
                where l.occurred_at = o.shipped_at
                """)).isEqualTo(2);
    }

    private void 확정한다(long outboundId) throws Exception {
        mvc.perform(post("/api/wholesale/outbounds/" + outboundId + "/ship")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk());
    }

    private Integer 정수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String 문자열(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
