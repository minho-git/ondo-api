package com.ondo.retail.settlement.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 거래 원장 한 줄 (MUL-129). 오래된 순으로 내려간다 — 화면이 위에서부터 누적 잔액을 쌓는다.
 *
 * <p>{@code delta}는 <b>출고 +, 입금 −</b>. 취소된 입금은 안 나온다(잘못 등록했다 되돌린 것이라 합이 0).
 *
 * @param kind        {@code SHIPMENT}(출고) · {@code PAYMENT}(입금)
 * @param statementNo 장끼 번호 {@code JG-20260812-004}. 출고 줄만
 * @param orderNo     출고된 주문의 주문번호. 출고 줄만. 소매에서 못 찾으면 null
 * @param method      {@code CASH} · {@code TRANSFER}. 입금 줄만
 * @param allocations 입금 줄만 — 이 입금이 붙은 주문들. <b>한 입금이 주문 여럿에 붙을 수 있다</b>
 * @param unallocated 입금 줄만 — 아직 어느 주문에도 안 붙은 돈
 */
public record LedgerEntryResponse(
        Long id,
        LocalDate date,
        String kind,
        String statementNo,
        String orderNo,
        String method,
        long delta,
        List<Allocation> allocations,
        Long unallocated) {

    /** @param orderNo 소매에서 못 찾으면 null */
    public record Allocation(String orderNo, long amount) {}
}
