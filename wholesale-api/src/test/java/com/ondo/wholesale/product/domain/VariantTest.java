package com.ondo.wholesale.product.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * variant 의 수량 도메인 메서드를 못박는다. 가용재고 검증은 호출부가 락 아래서
 * 끝내는 설계라(자바독 참조), 여기서는 델타 반영만 본다 — 새 variant 는 0에서 시작한다.
 */
class VariantTest {

    @Test
    void ship은_실재고와_예약을_함께_줄인다() {
        Variant variant = new Variant(null, null, null, 1);
        variant.reserve(5);

        variant.ship(3);

        assertThat(variant.getReservedQty()).isEqualTo(2);
        assertThat(variant.getStockQty()).isEqualTo(-3);
    }
}
