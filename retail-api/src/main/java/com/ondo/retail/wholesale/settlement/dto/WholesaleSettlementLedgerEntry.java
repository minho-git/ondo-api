package com.ondo.retail.wholesale.settlement.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 도매가 내려주는 원장 한 줄 그대로 (MUL-129). {@code retailOrderId}는 소매에 들어오면 그냥 자기 주문서 id 다. */
public record WholesaleSettlementLedgerEntry(
        Long id,
        String kind,
        LocalDate date,
        long delta,
        Long retailOrderId,
        Integer statementNumber,
        OffsetDateTime shippedAt,
        String method,
        List<Allocation> allocations,
        Long unallocated) {

    public record Allocation(Long retailOrderId, long amount) {}
}
