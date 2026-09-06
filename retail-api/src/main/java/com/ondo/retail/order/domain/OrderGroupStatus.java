package com.ondo.retail.order.domain;

/**
 * 통합 주문서 상태 (MUL-98).
 *
 * <p>도매처별 진행도가 아니다. 그건 도매 DB 에 있고 주문 상세가 물어본다.
 * 여기 있는 건 <b>이 주문서가 성립했는가</b>뿐이다.
 */
public enum OrderGroupStatus {

    /** 한 곳이라도 접수됐다. 넷 중 셋만 받아진 것도 여기다 — 부분 성공은 성공이다. */
    ACCEPTED,

    /** 전부 거절됐다. 사용자에게는 주문이 안 만들어진 것으로 보인다(502). */
    FAILED
}
