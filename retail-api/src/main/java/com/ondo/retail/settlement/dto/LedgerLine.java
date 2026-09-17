package com.ondo.retail.settlement.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 도매가 아는 만큼의 원장 한 줄 (MUL-129). {@link LedgerEntryResponse} 가 되기 전 모양이다.
 *
 * <p><b>주문번호 · 장끼 번호가 없다.</b> 주문번호는 소매 통합 주문서의 것이라 {@code orderId}(소매 주문서 id)로
 * 소매 DB 에서 채우고, 장끼 번호는 {@code shippedAt} · {@code statementNumber}로 조립한다.
 *
 * @param kind        {@code SHIPMENT} · {@code PAYMENT}
 * @param method      도매 말 그대로 — {@code CASH} · {@code BANK_TRANSFER}
 */
public record LedgerLine(
        Long id,
        String kind,
        LocalDate date,
        long delta,
        Long orderId,
        Integer statementNumber,
        OffsetDateTime shippedAt,
        String method,
        List<Allocation> allocations,
        Long unallocated) {

    /** @param orderId 소매 주문서 id */
    public record Allocation(Long orderId, long amount) {}
}
