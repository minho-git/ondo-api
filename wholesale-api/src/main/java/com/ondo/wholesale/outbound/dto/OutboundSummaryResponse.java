package com.ondo.wholesale.outbound.dto;

import com.ondo.wholesale.order.ReceiveBy;

import java.time.OffsetDateTime;

/**
 * 봉투 목록 한 행. "출고 완료" 뱃지는 {@code shippedAt != null}로 판정한다 — status 필드가 없다.
 * {@code receiveBy}는 null 이 되지 않는다(포장 완료가 수령 방식 혼합을 거절하므로 봉투당 하나).
 */
public record OutboundSummaryResponse(
        Long id,
        Integer outboundNumber,
        String summaryProductName,
        int additionalItemCount,
        ReceiveBy receiveBy,
        OffsetDateTime createdAt,
        OffsetDateTime shippedAt,
        Integer statementNumber,
        int totalQty
) {}
