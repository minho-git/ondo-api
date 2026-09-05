package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.repository.VariantRepository;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 확정형 배분 쓰기 통합 테스트 (MUL-47) — 배분·재고 예약·잔량 미송 생성을 실제 스키마에서 본다.
 * 요청 형식 검증은 AllocationValidatorTest 가 이미 본다.
 */
@SpringBootTest
@Transactional
class AllocationWriterTest extends PostgresTestSupport {

    @Autowired AllocationWriter writer;
    @Autowired OrderRepository orderRepository;
    @Autowired VariantRepository variantRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private long wholesalerId;
    private long partnerId;
    private long 니트;
    private long 슬랙스;

    @BeforeEach
    void 재료를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "writer@ondo.test", "9500000008");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        슬랙스 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "슬랙스", 2);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 701L, "행복상회");
        jdbc.update("update wholesale.variant set stock_qty = 10 where id in (?, ?)", 니트, 슬랙스);
    }

    @Test
    void 확정_배분을_반영하고_잔량은_미송이_된다() {
        long orderId = 주문(1);
        long 니트라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        long 슬랙스라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 슬랙스, 4, 2000, 0, 0);
        Order order = orderRepository.findById(orderId).orElseThrow();

        Packing packing = writer.confirmAllocate(wholesalerId, order,
                List.of(new LineAllocation(니트라인, 3), new LineAllocation(슬랙스라인, 0)));
        em.flush();

        assertThat(packing).isNotNull();
        assertThat(packing.getItems()).hasSize(1);
        assertThat(packing.getItems().get(0).getQty()).isEqualTo(3);
        assertThat(packing.getItems().get(0).getBackorderId()).isNull();
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 니트라인)).isEqualTo(3);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(3);
        assertThat(정수("select count(*) from wholesale.allocation_batch where wholesaler_id = " + wholesalerId)).isEqualTo(1);
        // 잔량이 미송이 된다 — 니트 5-3=2, 슬랙스 4-0=4
        assertThat(정수("select qty from wholesale.backorder where order_item_id = " + 니트라인)).isEqualTo(2);
        assertThat(정수("select qty from wholesale.backorder where order_item_id = " + 슬랙스라인)).isEqualTo(4);
        assertThat(문자열("select status from wholesale.backorder where order_item_id = " + 니트라인)).isEqualTo("OPEN");
    }

    @Test
    void 전부_0이면_포장_없이_전_라인이_미송이다() {
        long orderId = 주문(1);
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        Order order = orderRepository.findById(orderId).orElseThrow();

        Packing packing = writer.confirmAllocate(wholesalerId, order,
                List.of(new LineAllocation(라인, 0)));
        em.flush();

        assertThat(packing).isNull();
        assertThat(정수("select count(*) from wholesale.packing where order_id = " + orderId)).isZero();
        assertThat(정수("select qty from wholesale.backorder where order_item_id = " + 라인)).isEqualTo(5);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isZero();
    }

    @Test
    void 전량_배분이면_미송이_생기지_않는다() {
        long orderId = 주문(1);
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        Order order = orderRepository.findById(orderId).orElseThrow();

        writer.confirmAllocate(wholesalerId, order, List.of(new LineAllocation(라인, 5)));
        em.flush();

        assertThat(정수("select count(*) from wholesale.backorder where order_item_id = " + 라인)).isZero();
    }

    @Test
    void 가용재고를_정확히_다_쓰는_배분은_되고_넘으면_INSUFFICIENT_STOCK이다() {
        jdbc.update("update wholesale.variant set stock_qty = 5, reserved_qty = 2 where id = ?", 니트);
        long orderId = 주문(1);
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        Order order = orderRepository.findById(orderId).orElseThrow();

        assertThatThrownBy(() -> writer.confirmAllocate(wholesalerId, order,
                List.of(new LineAllocation(라인, 4))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        // 가용 3(=5-2)을 정확히 다 쓰는 배분은 성공한다
        writer.confirmAllocate(wholesalerId, order, List.of(new LineAllocation(라인, 3)));
        em.flush();
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(5);
    }

    @Test
    void 같은_variant를_쓰는_두_라인의_합이_가용을_넘으면_INSUFFICIENT_STOCK이다() {
        jdbc.update("update wholesale.variant set stock_qty = 3 where id = ?", 니트);
        long orderId = 주문(1);
        long 라인1 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 2, 1000, 0, 0);
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 2, 1000, 0, 0);
        Order order = orderRepository.findById(orderId).orElseThrow();

        assertThatThrownBy(() -> writer.confirmAllocate(wholesalerId, order,
                List.of(new LineAllocation(라인1, 2), new LineAllocation(라인2, 2))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    void release는_reserve로_잡은_예약을_되돌린다() {
        Variant variant = variantRepository.findById(니트).orElseThrow();

        variant.reserve(4);
        variant.release(3);
        em.flush();

        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(1);
    }

    private long 주문(int orderNumber) {
        return OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, orderNumber, "NEW",
                OffsetDateTime.now());
    }

    private int 정수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String 문자열(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
