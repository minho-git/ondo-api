package com.ondo.wholesale.settlement;

/** 미수원장 구분. SALE 판매(잔액 감소, 음수) / PAYMENT 입금(잔액 증가, 양수). */
public enum LedgerEntryType {
    SALE, PAYMENT
}
