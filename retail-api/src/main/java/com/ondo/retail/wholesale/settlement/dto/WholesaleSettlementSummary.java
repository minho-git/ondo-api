package com.ondo.retail.wholesale.settlement.dto;

import java.time.LocalDate;

/** 도매가 내려주는 도매처별 정산 한 줄 그대로 (MUL-129). 소매 DTO 와 타입을 가른 이유는 {@code WholesaleBackorder} 와 같다. */
public record WholesaleSettlementSummary(
        Long wholesalerId,
        String wholesalerName,
        long balance,
        Overdue overdue,
        LocalDate lastPaidAt,
        long paidLast7Days,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {

    public record Overdue(long amount, int count, int maxDays) {}
}
