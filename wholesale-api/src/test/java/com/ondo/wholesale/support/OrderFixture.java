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

    public static long 배분_배치를_넣는다(JdbcTemplate jdbc, long wholesalerId) {
        return jdbc.queryForObject("""
                insert into wholesale.allocation_batch (wholesaler_id) values (?) returning id
                """, Long.class, wholesalerId);
    }

    public static long 포장을_넣는다(JdbcTemplate jdbc, long orderId, String status) {
        return jdbc.queryForObject("""
                insert into wholesale.packing (order_id, status) values (?, ?) returning id
                """, Long.class, orderId, status);
    }

    /** deleted 가 참이면 배분취소된 항목으로 심는다. */
    public static long 포장항목을_넣는다(JdbcTemplate jdbc, long packingId, long orderItemId,
                                  Long backorderId, long batchId, int qty, boolean deleted) {
        return jdbc.queryForObject("""
                insert into wholesale.packing_item
                    (packing_id, order_item_id, backorder_id, allocation_batch_id, qty, deleted_at)
                values (?, ?, ?, ?, ?, case when ? then now() end) returning id
                """, Long.class, packingId, orderItemId, backorderId, batchId, qty, deleted);
    }

    /**
     * 출고 = 미수 발생(+). 원장 출고 줄은 출고 문서를 가리켜야 해서(V12) 봉투도 하나 만든다.
     * balance_after · 거래처 미수 칸은 검산용이라 여기선 맞추지 않는다 — 판정은 delta 와 배분으로만 한다.
     */
    public static void 원장_출고를_넣는다(JdbcTemplate jdbc, long partnerId, long orderId, long amount) {
        long outboundId = jdbc.queryForObject("""
                insert into wholesale.outbound (wholesaler_id, partner_id, outbound_number)
                select pt.wholesaler_id, pt.id,
                       coalesce((select max(outbound_number) from wholesale.outbound
                                 where wholesaler_id = pt.wholesaler_id), 0) + 1
                from wholesale.partner pt where pt.id = ?
                returning id
                """, Long.class, partnerId);
        jdbc.update("""
                insert into wholesale.receivable_ledger
                    (partner_id, request_id, entry_type, delta, balance_after, order_id, outbound_id, occurred_at)
                values (?, ?, 'OUTBOUND', ?, 0, ?, ?, now())
                """, partnerId, UUID.randomUUID().toString(), amount, orderId, outboundId);
    }

    /**
     * 이 주문 값으로 입금을 받았다 — 입금 한 건 + 원장 입금 줄(−, 주문 없음) + 그 주문에 배분.
     * 입금 줄은 주문을 가리키지 않으므로 "주문에 돈이 들어왔다"는 배분이 말한다 (MUL-126).
     */
    public static void 원장_입금을_넣는다(JdbcTemplate jdbc, long partnerId, long orderId, long amount) {
        long paymentId = jdbc.queryForObject("""
                insert into wholesale.payment
                    (partner_id, wholesaler_id, request_id, amount, paid_by, method, paid_at)
                select pt.id, pt.wholesaler_id, ?, ?, 'RETAILER', 'BANK_TRANSFER', now()
                from wholesale.partner pt where pt.id = ?
                returning id
                """, Long.class, UUID.randomUUID().toString(), amount, partnerId);
        jdbc.update("""
                insert into wholesale.receivable_ledger
                    (partner_id, request_id, entry_type, delta, balance_after, payment_id, occurred_at)
                values (?, ?, 'PAYMENT', ?, 0, ?, now())
                """, partnerId, UUID.randomUUID().toString(), -amount, paymentId);
        jdbc.update("insert into wholesale.payment_allocation (payment_id, order_id, amount) values (?, ?, ?)",
                paymentId, orderId, amount);
    }
}
