package com.ondo.wholesale.settlement.domain;

/**
 * 미수 원장 행 구분 — V1 {@code receivable_ledger_type_ck}의 3값 그대로 (MUL-49).
 *
 * <p>OUTBOUND 는 미수 발생(+), PAYMENT 는 입금(−), ADJUST 는 수동 보정(±).
 * 화면 표기용 {@code LedgerEntryType}(SALE·PAYMENT, 정산 계약 스텁)과는 다른 축이다 —
 * 이쪽은 저장 어휘고, 표기 매핑은 정산 티켓이 정한다.
 */
public enum ReceivableEntryType {
    OUTBOUND, PAYMENT, ADJUST
}
