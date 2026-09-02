package com.ondo.wholesale.settlement.dto;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.settlement.PaidBy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입금 등록 201 응답. 같은 키 재요청은 200 + 동일 본문(멱등). {@code unallocatedAmount} =
 * 선수금(amount − 배분 합계). {@code ledgerBalance}는 이 입금 반영 후의 거래처 미수 잔액
 * (음수 = 소매처 채무) — 좌측 아코디언 헤더를 재조회 없이 갱신할 수 있다.
 */
public record PaymentCreatedResponse(
        Long id,
        Long retailerId,
        String retailerName,
        int amount,
        OffsetDateTime paidAt,
        PaidBy paidBy,
        PaymentMethod method,
        String memo,
        int unallocatedAmount,
        List<Allocation> allocations,
        int ledgerBalance,
        OffsetDateTime createdAt
) {

    /** 만들어진 배분 하나. {@code orderNumber} 표기(ORD-006)는 프론트 조립. */
    public record Allocation(Long id, Long orderId, Integer orderNumber, int amount, OffsetDateTime createdAt) {}
}
