package com.ondo.wholesale.outbound;

/**
 * 출고 목록 status 필터. 저장된 상태가 아니다 — 출고에는 상태 컬럼이 없고 {@code shippedAt}의
 * null 여부로 갈린다. PACKED 를 안 쓰는 이유: 포장 카드의 상태값과 같은 문자열이 다른 것을
 * 가리키게 되기 때문.
 */
public enum OutboundStatusFilter {
    NOT_SHIPPED, SHIPPED
}
