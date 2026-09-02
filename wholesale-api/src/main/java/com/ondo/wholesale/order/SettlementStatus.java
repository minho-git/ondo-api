package com.ondo.wholesale.order;

/** 정산 상태. NEW·CANCELLED 주문에선 UNPAID 로 내려가지만 도메인상 의미가 없다. */
public enum SettlementStatus {
    UNPAID, PARTIALLY_SETTLED, SETTLED
}
