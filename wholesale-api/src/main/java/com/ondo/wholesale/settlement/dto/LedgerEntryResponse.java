package com.ondo.wholesale.settlement.dto;

import com.ondo.wholesale.settlement.LedgerEntryType;

import java.time.OffsetDateTime;

/**
 * 미수원장 한 줄. {@code balanceChange}는 부호 포함(판매 음수·입금 양수, 부호를 떼지 않는다),
 * {@code balanceAfter}는 그 거래 시점에 확정된 잔액 — 필터를 걸어도 안 바뀐다.
 * {@code orderId}·{@code orderNumber}는 SALE, {@code paymentId}는 PAYMENT·PAYMENT_VOID 일 때만 값이고 나머지는 null.
 */
public record LedgerEntryResponse(
        Long id,
        LedgerEntryType entryType,
        int balanceChange,
        int balanceAfter,
        OffsetDateTime occurredAt,
        Long orderId,
        Integer orderNumber,
        Long paymentId
) {}
