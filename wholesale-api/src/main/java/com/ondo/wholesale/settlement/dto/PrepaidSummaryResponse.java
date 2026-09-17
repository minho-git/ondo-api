package com.ondo.wholesale.settlement.dto;

/**
 * 거래처 선수금 요약 (MUL-125) — 정산 탭 3카드. 취소된 입금과 취소된 배분은 뺀다.
 * {@code prepaid = totalPaid − totalAllocated}.
 */
public record PrepaidSummaryResponse(
        Long retailerId,
        int totalPaid,
        int totalAllocated,
        int prepaid
) {}
