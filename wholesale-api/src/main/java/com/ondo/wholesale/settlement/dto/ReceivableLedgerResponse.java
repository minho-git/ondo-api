package com.ondo.wholesale.settlement.dto;

import com.ondo.wholesale.common.response.ResponseEnvelope;

import java.util.List;

/**
 * 미수원장 봉투 (api-lite/07_정산/GET_receivables.md) — 계약이 meta 에 표준 페이징 +
 * {@code ledgerBalance}(전체 기준 현재 잔액)를 함께 요구해 전용 봉투를 쓴다.
 * 화면 하단 "현재 잔액"은 {@code data[0].balanceAfter}가 아니라 {@code meta.ledgerBalance}다.
 */
public record ReceivableLedgerResponse(List<LedgerEntryResponse> data, LedgerMeta meta)
        implements ResponseEnvelope {

    /** 표준 페이징 + 전체 기준 최신 잔액. 필터·페이지와 무관하게 같은 값이다. */
    public record LedgerMeta(int page, int size, long totalElements, int totalPages, int ledgerBalance) {}
}
