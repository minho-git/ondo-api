package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

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
        OffsetDateTime 세시간전 = OffsetDateTime.now().minusHours(3);
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
}
