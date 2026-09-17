package com.ondo.wholesale.settlement.dto;

import java.time.OffsetDateTime;

/**
 * 입금 취소 200 응답 (MUL-127). {@code ledgerBalance}는 취소 반영 후 거래처 미수 잔액(음수 = 소매처 채무),
 * {@code prepaidRemaining}은 취소 후 거래처 선수금 — 좌측 헤더와 선수금 카드를 재조회 없이 갱신할 수 있다.
 */
public record PaymentVoidedResponse(
        Long paymentId,
        OffsetDateTime voidedAt,
        String voidReason,
        int ledgerBalance,
        int prepaidRemaining
) {}
