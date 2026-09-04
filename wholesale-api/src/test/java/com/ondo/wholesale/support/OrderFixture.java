package com.ondo.wholesale.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 주문 계열 통합 테스트가 공유하는 jdbc 픽스처 (MUL-47).
 *
 * <p>주문 생성 API 는 소매 접수(범위 밖)라 테스트는 행을 SQL 로 직접 심는다.
 * 마스터(도매처·카테고리·색상)는 {@link MasterDataFixture}를 먼저 쓴다.
 */
public final class OrderFixture {

    private OrderFixture() {
    }

    /** 상품 1 + 색상옵션 1 + FREE 변형 1을 넣고 variant id 를 돌려준다. */
    public static long 상품_변형을_넣는다(JdbcTemplate jdbc, long wholesalerId, long leafCategoryId,
                                   long colorId, String name, int productNumber) {
        long productId = jdbc.queryForObject("""
                insert into wholesale.product (wholesaler_id, product_number, name, category_id)
                values (?, ?, ?, ?) returning id
                """, Long.class, wholesalerId, productNumber, name, leafCategoryId);
        long colorOptionId = jdbc.queryForObject("""
                insert into wholesale.color_option (product_id, color_id)
                values (?, ?) returning id
                """, Long.class, productId, colorId);
        return jdbc.queryForObject("""
                insert into wholesale.variant (color_option_id, product_id, size, variant_seq)
                values (?, ?, 'FREE', 1) returning id
                """, Long.class, colorOptionId, productId);
    }

    public static long 거래처를_넣는다(JdbcTemplate jdbc, long wholesalerId, long retailerId, String name) {
        return jdbc.queryForObject("""
                insert into wholesale.partner (wholesaler_id, retailer_id, retailer_name)
                values (?, ?, ?) returning id
                """, Long.class, wholesalerId, retailerId, name);
    }

    public static long 주문을_넣는다(JdbcTemplate jdbc, long wholesalerId, long partnerId,
                                int orderNumber, String status, OffsetDateTime orderedAt) {
        return jdbc.queryForObject("""
                insert into wholesale.orders
                    (order_number, partner_id, wholesaler_id, status, payment_term, receive_method, ordered_at)
                values (?, ?, ?, ?, 'CASH', 'RETAILER', ?) returning id
                """, Long.class, orderNumber, partnerId, wholesalerId, status, orderedAt);
    }

    public static long 라인을_넣는다(JdbcTemplate jdbc, long orderId, long variantId,
                                int qty, int unitPrice, int allocated, int shipped) {
        return jdbc.queryForObject("""
                insert into wholesale.order_item
                    (order_id, variant_id, qty, unit_price, allocated_qty, shipped_qty)
                values (?, ?, ?, ?, ?, ?) returning id
                """, Long.class, orderId, variantId, qty, unitPrice, allocated, shipped);
    }

    public static long 미송을_넣는다(JdbcTemplate jdbc, long orderItemId, int qty, String status) {
        return jdbc.queryForObject("""
                insert into wholesale.backorder (order_item_id, qty, status)
                values (?, ?, ?) returning id
                """, Long.class, orderItemId, qty, status);
    }

    /** 출고 = 미수 발생(+). */
    public static void 원장_출고를_넣는다(JdbcTemplate jdbc, long partnerId, long orderId, long amount) {
        원장을_넣는다(jdbc, partnerId, orderId, "OUTBOUND", amount);
    }

    /** 입금 = 미수 감소(−). */
    public static void 원장_입금을_넣는다(JdbcTemplate jdbc, long partnerId, long orderId, long amount) {
        원장을_넣는다(jdbc, partnerId, orderId, "PAYMENT", -amount);
    }

    private static void 원장을_넣는다(JdbcTemplate jdbc, long partnerId, long orderId, String type, long delta) {
        // balance_after 는 검산용 컬럼이라 테스트에선 0 으로 둔다 — 판정은 delta 합으로만 한다
        jdbc.update("""
                insert into wholesale.receivable_ledger
                    (partner_id, request_id, entry_type, delta, balance_after, order_id, occurred_at)
                values (?, ?, ?, ?, 0, ?, now())
                """, partnerId, UUID.randomUUID().toString(), type, delta, orderId);
    }
}
