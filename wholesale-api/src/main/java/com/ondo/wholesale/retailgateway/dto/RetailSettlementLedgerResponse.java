package com.ondo.wholesale.retailgateway.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 소매처 · 도매처 사이 거래 원장 한 줄 (MUL-129). 오래된 순으로 내려간다.
 *
 * <p>{@code kind}는 {@code SHIPMENT}(출고, +) · {@code PAYMENT}(입금, −) 둘뿐이다. 취소된 입금은 그 입금 줄과
 * 취소 줄을 둘 다 뺀다 — 합이 0 이라 잔액은 그대로고, 소매처가 볼 이유가 없다.
 *
 * <p>주문번호와 장끼 번호는 안 준다. 주문번호는 소매 통합 주문서의 것이라 {@code retailOrderId}로 소매가 채우고,
 * 장끼 표시 코드({@code JG-20260818-001})는 받는 쪽이 {@code shippedAt} · {@code statementNumber}로 조립한다.
 *
 * @param date          KST 날짜 — 출고는 출고일, 입금은 입금일
 * @param retailOrderId 출고 줄만. 도매 화면에서 직접 넣은 주문이면 null
 * @param method        입금 줄만 — {@code CASH} · {@code BANK_TRANSFER}
 * @param allocations   입금 줄만 — 어느 주문에 얼마 붙었나(취소된 배분 제외). 한 입금이 주문 여럿에 붙을 수 있다
 * @param unallocated   입금 줄만 — 아직 어느 주문에도 안 붙은 돈
 */
public record RetailSettlementLedgerResponse(
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
