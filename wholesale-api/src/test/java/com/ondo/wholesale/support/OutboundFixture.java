package com.ondo.wholesale.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

/**
 * 출고 계열 통합 테스트가 공유하는 jdbc 픽스처 (MUL-49).
 *
 * <p>주문 쪽 재료(상품·거래처·라인·포장)는 {@link OrderFixture}를 그대로 쓰고,
 * 여기는 출고 문서와 수령 방식이 다른 주문 등 출고 전용 재료만 둔다.
 */
public final class OutboundFixture {

    private OutboundFixture() {
    }

    /** 확정 주문을 수령 방식까지 지정해 넣는다 — 수령 방식 혼합 검증 재료. */
    public static long 확정주문을_넣는다(JdbcTemplate jdbc, long wholesalerId, long partnerId,
                                  int orderNumber, String receiveBy, OffsetDateTime orderedAt) {
        return jdbc.queryForObject("""
                insert into wholesale.orders
                    (order_number, partner_id, wholesaler_id, status, payment_term, receive_method, ordered_at)
                values (?, ?, ?, 'CONFIRMED', 'CASH', ?, ?) returning id
                """, Long.class, orderNumber, partnerId, wholesalerId, receiveBy, orderedAt);
    }

    /** 포장완료 상태의 출고(봉투)를 넣는다 — shipped_at·statement_number 는 NULL. */
    public static long 출고를_넣는다(JdbcTemplate jdbc, long wholesalerId, long partnerId, int outboundNumber) {
        return jdbc.queryForObject("""
                insert into wholesale.outbound (wholesaler_id, partner_id, outbound_number)
                values (?, ?, ?) returning id
                """, Long.class, wholesalerId, partnerId, outboundNumber);
    }

    /** 출고에 묶인 PACKED 포장을 넣는다. */
    public static long 묶인_포장을_넣는다(JdbcTemplate jdbc, long orderId, long outboundId) {
        return jdbc.queryForObject("""
                insert into wholesale.packing (order_id, outbound_id, status)
                values (?, ?, 'PACKED') returning id
                """, Long.class, orderId, outboundId);
    }

    /** 출고를 확정 상태로 만든다 — 장끼번호와 출고일시를 채운다. */
    public static void 출고를_확정한다(JdbcTemplate jdbc, long outboundId,
                                 int statementNumber, OffsetDateTime shippedAt) {
        jdbc.update("update wholesale.outbound set statement_number = ?, shipped_at = ? where id = ?",
                statementNumber, shippedAt, outboundId);
    }
}
