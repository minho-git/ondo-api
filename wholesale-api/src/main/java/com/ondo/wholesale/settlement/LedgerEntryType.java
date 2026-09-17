package com.ondo.wholesale.settlement;

/**
 * 미수원장 구분 — 화면 어휘. 저장 어휘({@code ReceivableEntryType})와 이름이 다르다: 출고(OUTBOUND)를 화면은
 * 판매(SALE)라 부른다. 부호도 화면 계약대로 판매 음수 · 입금 양수다({@link LedgerSign}).
 *
 * <p>PAYMENT_VOID(입금 취소, 음수)와 ADJUST(수기 조정)는 MUL-126 에서 더했다 — 만드는 API 는 MUL-127 이후다.
 */
public enum LedgerEntryType {
    SALE, PAYMENT, PAYMENT_VOID, ADJUST
}
