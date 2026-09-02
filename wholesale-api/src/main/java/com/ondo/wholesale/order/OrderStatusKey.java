package com.ondo.wholesale.order;

/**
 * 주문 상태 표시값. 저장값이 아니다 — DB 는 NEW/CONFIRMED/CANCELLED 3값이고,
 * 출고 진행도(shippedQty)를 합쳐 5값으로 만든다. 조합 규칙은 서버 안에만 둔다.
 */
public enum OrderStatusKey {
    NEW, CONFIRMED, PARTIALLY_SHIPPED, SHIPPED, CANCELLED
}
