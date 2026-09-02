package com.ondo.wholesale.order;

/**
 * 주문 목록 상태 칩 식별자. {@link OrderStatusKey}와 어휘가 겹치지만 뜻이 다르다 —
 * 칩 CONFIRMED 는 "확정 + 미출고"만 센다. ALL 은 칩에만 있다.
 */
public enum OrderFilterKey {
    ALL, NEW, CONFIRMED, PARTIALLY_SHIPPED, SHIPPED, CANCELLED
}
