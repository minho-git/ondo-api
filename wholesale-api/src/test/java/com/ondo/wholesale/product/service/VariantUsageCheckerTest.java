package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * variant 삭제 가능 검사 (MUL-93 2단계). 주문·미송 테이블은 엔티티가 없어
 * jdbc 로 행을 직접 심어 4가지 걸림 조건과 검사 순서를 검증한다.
 */
@SpringBootTest
@Transactional
class VariantUsageCheckerTest extends PostgresTestSupport {

    @Autowired
    private VariantUsageChecker checker;

    @Autowired
    private JdbcTemplate jdbc;

    private long wholesalerId;
    private long variantId;

    @BeforeEach
    void 상품과_variant를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "checker@ondo.test", "9600000001");
        long leaf = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9601);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9600, 9610);
        long productId = jdbc.queryForObject("""
                insert into wholesale.product (wholesaler_id, product_number, name, category_id)
                values (?, 1, '검사 상품', ?) returning id
                """, Long.class, wholesalerId, leaf);
        long optionId = jdbc.queryForObject("""
                insert into wholesale.color_option (product_id, color_id) values (?, ?) returning id
                """, Long.class, productId, colorId);
        variantId = jdbc.queryForObject("""
                insert into wholesale.variant (color_option_id, product_id, size, variant_seq)
                values (?, ?, 'S', 1) returning id
                """, Long.class, optionId, productId);
    }

    @Test
    void 재고가_있으면_VARIANT_HAS_STOCK이다() {
        jdbc.update("update wholesale.variant set stock_qty = 3 where id = ?", variantId);
        검사하면_걸린다(ErrorCode.VARIANT_HAS_STOCK);
    }

    @Test
    void 배분이_있으면_VARIANT_ALLOCATED다() {
        jdbc.update("update wholesale.variant set reserved_qty = 1 where id = ?", variantId);
        검사하면_걸린다(ErrorCode.VARIANT_ALLOCATED);
    }

    @Test
    void OPEN_미송이_있으면_VARIANT_HAS_BACKORDER다() {
        long orderItemId = 주문과_아이템을_넣는다("CANCELLED"); // 주문 상태와 무관하게 미송 자체를 본다
        jdbc.update("insert into wholesale.backorder (order_item_id, qty, status) values (?, 2, 'OPEN')", orderItemId);
        검사하면_걸린다(ErrorCode.VARIANT_HAS_BACKORDER);
    }

    @Test
    void 처리중_주문에_잡혀있으면_VARIANT_IN_PENDING_ORDER다() {
        주문과_아이템을_넣는다("CONFIRMED");
        검사하면_걸린다(ErrorCode.VARIANT_IN_PENDING_ORDER);
    }

    @Test
    void 취소된_주문과_해소된_미송만_있으면_지울_수_있다() {
        long orderItemId = 주문과_아이템을_넣는다("CANCELLED");
        jdbc.update("insert into wholesale.backorder (order_item_id, qty, status) values (?, 2, 'RESOLVED')", orderItemId);

        assertThatCode(() -> checker.ensureDeletable(List.of(variantId))).doesNotThrowAnyException();
    }

    @Test
    void 여러_조건에_걸리면_재고가_먼저다() {
        jdbc.update("update wholesale.variant set stock_qty = 3, reserved_qty = 1 where id = ?", variantId);
        주문과_아이템을_넣는다("NEW");
        검사하면_걸린다(ErrorCode.VARIANT_HAS_STOCK); // 계약 나열 순서: 재고 → 배분 → 미송 → 주문
    }

    private void 검사하면_걸린다(ErrorCode expected) {
        assertThatThrownBy(() -> checker.ensureDeletable(List.of(variantId)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(expected));
    }

    private long 주문과_아이템을_넣는다(String orderStatus) {
        long partnerId = jdbc.queryForObject("""
                insert into wholesale.partner (wholesaler_id, retailer_id, retailer_name)
                values (?, 1, '소매상') returning id
                """, Long.class, wholesalerId);
        long orderId = jdbc.queryForObject("""
                insert into wholesale.orders (order_number, partner_id, wholesaler_id, status,
                                              payment_term, receive_method, ordered_at)
                values (1, ?, ?, ?, 'CASH', 'PICKUP', now()) returning id
                """, Long.class, partnerId, wholesalerId, orderStatus);
        return jdbc.queryForObject("""
                insert into wholesale.order_item (order_id, variant_id, qty, unit_price)
                values (?, ?, 2, 29000) returning id
                """, Long.class, orderId, variantId);
    }
}
