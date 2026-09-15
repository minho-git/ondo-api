package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.OutboundFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 집계 쿼리 검증 (MUL-120) — 리더를 직접 호출한다.
 * HTTP 계약은 {@code DashboardApiTest}, 조립은 summary 통합 테스트가 본다.
 */
@SpringBootTest
@Transactional
class DashboardSummaryReaderIntegrationTest extends PostgresTestSupport {

    @Autowired DashboardSummaryReader reader;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long leafId;
    private long colorId;
    private long 봄봄;
    private long 모모샵;
    private long variantId;
    private int nextOrderNumber = 1;

    @BeforeEach
    void 도매처와_마스터를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "dashboard-reader@ondo.test", "9500000050");
        leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9380);
        colorId = MasterDataFixture.색상을_넣는다(jdbc, 9480, 9481);
        봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 751L, "봄봄");
        모모샵 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 752L, "모모샵");
        variantId = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "티셔츠", 1);
    }

    @Test
    void 신규_주문만_세고_가장_오래된_접수와_소매처를_찾는다() {
        // timestamptz 는 마이크로초까지만 저장한다 — 나노초가 있으면 (Linux CI) 왕복 후 비교가 깨진다
        OffsetDateTime 세시간전 = OffsetDateTime.now().minusHours(3).truncatedTo(ChronoUnit.MICROS);
        OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++, "NEW", 세시간전);
        OrderFixture.주문을_넣는다(jdbc, wholesalerId, 모모샵, nextOrderNumber++, "NEW",
                OffsetDateTime.now().minusHours(1));
        OrderFixture.주문을_넣는다(jdbc, wholesalerId, 모모샵, nextOrderNumber++, "CONFIRMED",
                OffsetDateTime.now().minusHours(5));

        var agg = reader.newOrders(wholesalerId);

        assertThat(agg.count()).isEqualTo(2);
        assertThat(agg.oldestOrderedAt().toInstant()).isEqualTo(세시간전.toInstant());
        assertThat(agg.oldestRetailerName()).isEqualTo("봄봄");
    }

    @Test
    void 신규_주문이_없으면_0과_빈_값이다() {
        OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++, "CONFIRMED",
                OffsetDateTime.now());

        var agg = reader.newOrders(wholesalerId);

        assertThat(agg.count()).isZero();
        assertThat(agg.oldestOrderedAt()).isNull();
        assertThat(agg.oldestRetailerName()).isNull();
    }

    @Test
    void 영업일_시작_이후_주문만_건수와_금액에_잡힌다() {
        OffsetDateTime 영업일시작 = OffsetDateTime.now().minusHours(2);
        // 경계 뒤: 확정 1건(2장 × 10,000) + 취소 1건(1장 × 5,000) — 건수·금액은 취소 포함
        long 확정 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now().minusHours(1));
        OrderFixture.라인을_넣는다(jdbc, 확정, variantId, 2, 10000, 0, 0);
        long 취소 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 모모샵, nextOrderNumber++,
                "CANCELLED", OffsetDateTime.now().minusMinutes(30));
        OrderFixture.라인을_넣는다(jdbc, 취소, variantId, 1, 5000, 0, 0);
        // 경계 앞: 지난 영업일 주문은 빠진다
        long 어제 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now().minusHours(3));
        OrderFixture.라인을_넣는다(jdbc, 어제, variantId, 9, 1000, 0, 0);

        var agg = reader.todayOrders(wholesalerId, 영업일시작);

        assertThat(agg.count()).isEqualTo(2);
        assertThat(agg.amount()).isEqualTo(25000);
        assertThat(agg.cancelled()).isEqualTo(1);
    }

    @Test
    void 포장_대기를_소매처와_수령방식별로_센다() {
        long batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);
        // 봄봄(AGENT): 대기 항목 10장 + 배분취소된 항목 5장(빠짐)
        long 봄봄주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄,
                nextOrderNumber++, "AGENT", OffsetDateTime.now());
        long 봄봄라인 = OrderFixture.라인을_넣는다(jdbc, 봄봄주문, variantId, 15, 1000, 15, 0);
        long 봄봄포장 = OrderFixture.포장을_넣는다(jdbc, 봄봄주문, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 봄봄포장, 봄봄라인, null, batchId, 10, false);
        OrderFixture.포장항목을_넣는다(jdbc, 봄봄포장, 봄봄라인, null, batchId, 5, true);
        // 모모샵(RETAILER): 대기 항목 7장
        long 모모주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 모모샵,
                nextOrderNumber++, "RETAILER", OffsetDateTime.now());
        long 모모라인 = OrderFixture.라인을_넣는다(jdbc, 모모주문, variantId, 7, 1000, 7, 0);
        long 모모포장 = OrderFixture.포장을_넣는다(jdbc, 모모주문, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 모모포장, 모모라인, null, batchId, 7, false);
        // 이미 봉투에 담긴 포장은 대기가 아니다
        long 담긴주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄,
                nextOrderNumber++, "AGENT", OffsetDateTime.now());
        long 담긴라인 = OrderFixture.라인을_넣는다(jdbc, 담긴주문, variantId, 3, 1000, 3, 0);
        long 봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);
        long 담긴포장 = OutboundFixture.묶인_포장을_넣는다(jdbc, 담긴주문, 봉투);
        OrderFixture.포장항목을_넣는다(jdbc, 담긴포장, 담긴라인, null, batchId, 3, false);

        var agg = reader.packing(wholesalerId);

        assertThat(agg.retailerCount()).isEqualTo(2);
        assertThat(agg.qty()).isEqualTo(17);
        assertThat(agg.byReceive()).isEqualTo(Map.of(ReceiveBy.AGENT, 1, ReceiveBy.RETAILER, 1));
    }

    @Test
    void 미출고_봉투와_영업일_이전_포장분을_구분해_센다() {
        OffsetDateTime 영업일시작 = OffsetDateTime.now().minusHours(2);
        OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);                 // 방금 포장
        long 묵은봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 2);   // 지난 영업일 포장
        jdbc.update("update wholesale.outbound set created_at = now() - interval '3 hours' where id = ?",
                묵은봉투);
        long 확정봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 모모샵, 3);  // 이미 출고 확정
        OutboundFixture.출고를_확정한다(jdbc, 확정봉투, 1, OffsetDateTime.now());

        var agg = reader.outbound(wholesalerId, 영업일시작);

        assertThat(agg.notShippedCount()).isEqualTo(2);
        assertThat(agg.staleCount()).isEqualTo(1);
    }

    @Test
    void 영업일_시작_이후_출고만_봉투와_장수에_잡힌다() {
        OffsetDateTime 영업일시작 = OffsetDateTime.now().minusHours(2);
        long batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);
        // 경계 뒤 출고: 4장
        long 오늘봉투 = 포장된_봉투를_넣는다(봄봄, 1, batchId, 4);
        OutboundFixture.출고를_확정한다(jdbc, 오늘봉투, 1, OffsetDateTime.now().minusHours(1));
        // 경계 앞 출고: 9장 — 빠진다
        long 어제봉투 = 포장된_봉투를_넣는다(모모샵, 2, batchId, 9);
        OutboundFixture.출고를_확정한다(jdbc, 어제봉투, 2, OffsetDateTime.now().minusHours(3));

        var agg = reader.todayShipped(wholesalerId, 영업일시작);

        assertThat(agg.count()).isEqualTo(1);
        assertThat(agg.qty()).isEqualTo(4);
    }

    @Test
    void 미송_SKU_수와_장수와_입고일_상태를_집계한다() {
        // 입고일 지난 SKU: 잔여 3
        long 지연SKU = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "지연니트", 2);
        jdbc.update("update wholesale.variant set expected_inbound_date = ? where id = ?",
                LocalDate.now().minusDays(1), 지연SKU);
        미송라인을_넣는다(지연SKU, 5, 2, "OPEN");
        // 입고일 미등록 SKU: 잔여 4
        long 미등록SKU = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "미등록셔츠", 3);
        미송라인을_넣는다(미등록SKU, 4, 0, "OPEN");
        // 해소된 미송만 있는 SKU 는 빠진다
        long 해소SKU = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "해소팬츠", 4);
        미송라인을_넣는다(해소SKU, 6, 6, "RESOLVED");

        var agg = reader.backorder(wholesalerId, LocalDate.now());

        assertThat(agg.skuCount()).isEqualTo(2);
        assertThat(agg.qty()).isEqualTo(7);
        assertThat(agg.overdueSkuCount()).isEqualTo(1);
        assertThat(agg.noDateSkuCount()).isEqualTo(1);
    }

    @Test
    void 다른_도매처의_데이터는_집계에_안_섞인다() {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "dashboard-other@ondo.test", "9500000051");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 753L, "남의상회");
        long 남SKU = OrderFixture.상품_변형을_넣는다(jdbc, 남, leafId, colorId, "남의니트", 1);
        long 남배치 = OrderFixture.배분_배치를_넣는다(jdbc, 남);
        // 남의 포장 대기·미출고 봉투·미송을 전부 심는다
        long 남주문 = OutboundFixture.확정주문을_넣는다(jdbc, 남, 남거래처,
                1, "AGENT", OffsetDateTime.now());
        long 남라인 = OrderFixture.라인을_넣는다(jdbc, 남주문, 남SKU, 5, 1000, 2, 0);
        long 남포장 = OrderFixture.포장을_넣는다(jdbc, 남주문, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 남포장, 남라인, null, 남배치, 3, false);
        OutboundFixture.출고를_넣는다(jdbc, 남, 남거래처, 1);
        OrderFixture.미송을_넣는다(jdbc, 남라인, 3, "OPEN");

        assertThat(reader.packing(wholesalerId).retailerCount()).isZero();
        assertThat(reader.packing(wholesalerId).qty()).isZero();
        assertThat(reader.outbound(wholesalerId, OffsetDateTime.now().minusHours(2)).notShippedCount()).isZero();
        assertThat(reader.todayShipped(wholesalerId, OffsetDateTime.now().minusHours(2)).count()).isZero();
        assertThat(reader.backorder(wholesalerId, LocalDate.now()).skuCount()).isZero();
    }

    /** 확정 주문 → 라인 → 봉투에 담긴 PACKED 포장 → 포장항목(qty)까지 한 번에 심는다. */
    private long 포장된_봉투를_넣는다(long partnerId, int outboundNumber, long batchId, int qty) {
        long 주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, partnerId,
                nextOrderNumber++, "RETAILER", OffsetDateTime.now());
        long 라인 = OrderFixture.라인을_넣는다(jdbc, 주문, variantId, qty, 1000, qty, qty);
        long 봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, partnerId, outboundNumber);
        long 포장 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문, 봉투);
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, null, batchId, qty, false);
        return 봉투;
    }

    /** 확정 주문에 라인(qty, allocated)과 미송 한 건을 단다 — 잔여 = qty − allocated. */
    private void 미송라인을_넣는다(long variantId, int qty, int allocated, String status) {
        long 주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 라인 = OrderFixture.라인을_넣는다(jdbc, 주문, variantId, qty, 1000, allocated, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인, qty - allocated, status);
    }
}
