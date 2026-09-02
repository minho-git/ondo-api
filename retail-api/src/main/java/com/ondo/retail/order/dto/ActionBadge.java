package com.ondo.retail.order.dto;

/**
 * 통합 주문서에 "지금 내가 할 일" 을 붙인다.
 *
 * <p>여기 상태 이름을 안 쓴다. 도매처가 둘인데 하나는 확정, 하나는 출고 중이면
 * "이 주문의 상태" 라는 게 없다. 어느 이름을 골라도 나머지에 대해 거짓이 된다.
 *
 * <p>위에서부터 먼저 걸리는 것을 쓴다. 일부가 취소돼도 남은 게 있으면 그쪽을 보여준다.
 */
public enum ActionBadge {
    /** 하나라도 NEW — 도매처가 아직 안 받았어요 */
    PENDING_ACCEPT,
    /** 전부 CONFIRMED 이상 — 준비 중이에요 */
    WAITING_SHIPMENT,
    /** 하나라도 부분 출고·출고 — 찾아가실 수 있어요 */
    READY_TO_PICK_UP,
    /** 전부 SHIPPED — 완료 */
    DONE,
    /** 전부 CANCELLED — 취소됨 */
    CANCELLED
}
