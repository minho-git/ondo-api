package com.ondo.retail.settlement.dto;

import java.time.LocalDate;

/**
 * 도매가 계산해 준 도매처별 정산 한 줄 (MUL-129). {@link PartnerSettlementResponse} 가 되기 전 모양이다.
 * 소매가 더 채울 게 없어 그대로 옮기지만, 도매 필드가 바뀌면 어댑터가 컴파일로 먼저 막도록 타입을 따로 둔다.
 */
public record SettlementSummaryLine(
        Long wholesalerId,
        String wholesalerName,
        long balance,
        long overdueAmount,
        int overdueCount,
        int overdueMaxDays,
        LocalDate lastPaidAt,
        long paidLast7Days,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {
}
