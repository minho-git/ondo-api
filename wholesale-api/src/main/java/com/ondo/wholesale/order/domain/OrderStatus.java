package com.ondo.wholesale.order.domain;

/**
 * 주문의 저장 상태. DB CHECK 와 같은 3값이다.
 *
 * <p>화면에 내리는 표시 상태({@link com.ondo.wholesale.order.OrderStatusKey})는 5값 —
 * 여기 3값에 출고 진행도(shippedQty 합)를 합쳐 파생한다. 조합 규칙은 서버 안에만 둔다.
 */
public enum OrderStatus {
    NEW, CONFIRMED, CANCELLED
}
