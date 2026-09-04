package com.ondo.wholesale.order.domain;

/**
 * 미송 상태. DB CHECK 와 같은 3값이다.
 *
 * <p>RESOLVED 는 잔량 파생(qty − allocated_qty)으로도 알 수 있지만 조회 성능을 위해 저장한다.
 */
public enum BackorderStatus {
    OPEN, RESOLVED, CANCELLED
}
