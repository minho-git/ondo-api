package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 라인의 출고 카운터 단위 검증 (MUL-49). 수량 사다리(shipped ≤ allocated ≤ qty)
 * 검증은 호출부(출고 확정)가 락 아래서 끝낸다 — 여기는 카운터 반영만 본다.
 */
class OrderItemTest {

    @Test
    void ship은_출고수량을_올린다() {
        Order order = Order.builder()
                .orderNumber(1).partnerId(1L).wholesalerId(1L)
                .paymentTerm(PaymentMethod.CASH).receiveMethod(ReceiveBy.RETAILER)
                .orderedAt(OffsetDateTime.now()).build();
        OrderItem item = order.addItem(10L, 5, 1000);
        item.allocate(3);

        item.ship(2);

        assertThat(item.getShippedQty()).isEqualTo(2);
    }
}
