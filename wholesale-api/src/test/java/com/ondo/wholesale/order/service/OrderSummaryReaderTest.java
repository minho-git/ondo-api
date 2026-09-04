package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.SettlementStatus;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 목록 배치 조회(첫 라인·수량 합계·정산·칩 건수) 통합 검증 (MUL-47).
 */
@SpringBootTest
@Transactional
class OrderSummaryReaderTest extends PostgresTestSupport {

    @Autowired OrderSummaryReader reader;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long 니트;
    private long 슬랙스;

    @BeforeEach
    void 재료를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "reader@ondo.test", "9500000003");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        슬랙스 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "슬랙스", 2);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 701L, "행복상회");
    }

    @Test
    void 첫_라인은_라인_id가_가장_작은_것이고_외_N건은_나머지_수다() {
        long orderId = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 3, 1000, 0, 0);
        OrderFixture.라인을_넣는다(jdbc, orderId, 슬랙스, 5, 2000, 0, 0);

        OrderSummaryReader.FirstLine line = reader.firstLines(List.of(orderId)).get(orderId);

        assertThat(line.productName()).isEqualTo("니트");
        assertThat(line.colorName()).isEqualTo("블랙");
        assertThat(line.additionalCount()).isEqualTo(1);
    }

    @Test
    void 라인이_하나면_외_N건이_0이다() {
        long orderId = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 3, 1000, 0, 0);

        assertThat(reader.firstLines(List.of(orderId)).get(orderId).additionalCount()).isZero();
    }

    @Test
    void 수량과_금액_합계를_주문별로_읽는다() {
        long orderId = 주문(1, "CONFIRMED");
        OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 3, 1000, 2, 1);
        OrderFixture.라인을_넣는다(jdbc, orderId, 슬랙스, 5, 2000, 0, 0);

        OrderSummaryReader.QtySums sums = reader.qtySums(List.of(orderId)).get(orderId);

        assertThat(sums.totalQty()).isEqualTo(8);
        assertThat(sums.allocatedSum()).isEqualTo(2);
        assertThat(sums.shippedSum()).isEqualTo(1);
        assertThat(sums.orderAmount()).isEqualTo(13000);
    }

    @Test
    void 원장이_없으면_UNPAID에_미수_0이다() {
        long orderId = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 3, 1000, 0, 0);

        OrderSummaryReader.Settlement s = reader.settlements(List.of(orderId)).get(orderId);

        assertThat(s.status()).isEqualTo(SettlementStatus.UNPAID);
        assertThat(s.outstandingAmount()).isZero();
    }

    @Test
    void 입금이_일부면_PARTIALLY_SETTLED고_전액이면_SETTLED다() {
        long 일부 = 주문(1, "CONFIRMED");
        OrderFixture.원장_출고를_넣는다(jdbc, partnerId, 일부, 10000);
        OrderFixture.원장_입금을_넣는다(jdbc, partnerId, 일부, 4000);
        long 전액 = 주문(2, "CONFIRMED");
        OrderFixture.원장_출고를_넣는다(jdbc, partnerId, 전액, 5000);
        OrderFixture.원장_입금을_넣는다(jdbc, partnerId, 전액, 5000);

        Map<Long, OrderSummaryReader.Settlement> map = reader.settlements(List.of(일부, 전액));

        assertThat(map.get(일부).status()).isEqualTo(SettlementStatus.PARTIALLY_SETTLED);
        assertThat(map.get(일부).outstandingAmount()).isEqualTo(6000);
        assertThat(map.get(전액).status()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(map.get(전액).outstandingAmount()).isZero();
    }

    @Test
    void 정산_상태로_주문_id를_고른다() {
        long 미입금 = 주문(1, "CONFIRMED");
        long 완납 = 주문(2, "CONFIRMED");
        OrderFixture.원장_출고를_넣는다(jdbc, partnerId, 완납, 5000);
        OrderFixture.원장_입금을_넣는다(jdbc, partnerId, 완납, 5000);

        assertThat(reader.orderIdsBySettlement(wholesalerId, SettlementStatus.UNPAID))
                .contains(미입금).doesNotContain(완납);
        assertThat(reader.orderIdsBySettlement(wholesalerId, SettlementStatus.SETTLED))
                .contains(완납).doesNotContain(미입금);
    }

    @Test
    void 칩_건수는_파생_상태_버킷으로_세고_CONFIRMED는_미출고만_센다() {
        신규_확정_부분출고_전량출고_취소를_한_건씩_심는다();

        Map<OrderFilterKey, Long> chips = reader.chipCounts(wholesalerId, null, null, null);

        assertThat(chips.get(OrderFilterKey.ALL)).isEqualTo(5);
        assertThat(chips.get(OrderFilterKey.NEW)).isEqualTo(1);
        assertThat(chips.get(OrderFilterKey.CONFIRMED)).isEqualTo(1);
        assertThat(chips.get(OrderFilterKey.PARTIALLY_SHIPPED)).isEqualTo(1);
        assertThat(chips.get(OrderFilterKey.SHIPPED)).isEqualTo(1);
        assertThat(chips.get(OrderFilterKey.CANCELLED)).isEqualTo(1);
    }

    @Test
    void 칩도_검색어와_기간을_따른다() {
        long 행복주문 = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, 행복주문, 니트, 1, 1000, 0, 0);
        long 기쁨거래처 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 702L, "기쁨상회");
        long 기쁨주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 기쁨거래처, 2, "NEW",
                OffsetDateTime.now());
        OrderFixture.라인을_넣는다(jdbc, 기쁨주문, 슬랙스, 1, 1000, 0, 0);

        assertThat(reader.chipCounts(wholesalerId, "기쁨", null, null).get(OrderFilterKey.ALL))
                .isEqualTo(1);
        assertThat(reader.chipCounts(wholesalerId, null, LocalDate.now().plusDays(1), null)
                .get(OrderFilterKey.ALL)).isZero();
        assertThat(reader.chipCounts(wholesalerId, null, null, LocalDate.now().minusDays(1))
                .get(OrderFilterKey.ALL)).isZero();
    }

    private long 주문(int orderNumber, String status) {
        return OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, orderNumber, status,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void 신규_확정_부분출고_전량출고_취소를_한_건씩_심는다() {
        long 신규 = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, 신규, 니트, 3, 1000, 0, 0);
        long 확정 = 주문(2, "CONFIRMED");
        OrderFixture.라인을_넣는다(jdbc, 확정, 니트, 3, 1000, 3, 0);
        long 부분 = 주문(3, "CONFIRMED");
        OrderFixture.라인을_넣는다(jdbc, 부분, 니트, 3, 1000, 3, 1);
        long 전량 = 주문(4, "CONFIRMED");
        OrderFixture.라인을_넣는다(jdbc, 전량, 니트, 3, 1000, 3, 3);
        long 취소 = 주문(5, "CANCELLED");
        OrderFixture.라인을_넣는다(jdbc, 취소, 니트, 3, 1000, 0, 0);
    }
}
