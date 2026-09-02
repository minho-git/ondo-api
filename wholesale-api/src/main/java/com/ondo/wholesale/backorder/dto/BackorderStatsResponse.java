package com.ondo.wholesale.backorder.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 우측 "미송 요약" 패널. {@code backorderAmount} = Σ(remainingQty × 주문 시점 단가) —
 * 단가가 주문마다 달라 프론트가 현재 판매가로 다시 계산하면 틀린다.
 * {@code orderCount}는 미송 건수가 아니라 OPEN 미송을 가진 주문 수.
 */
public record BackorderStatsResponse(
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        int backorderQty,
        int orderCount,
        int retailerCount,
        int availableQty,
        LocalDate expectedInboundDate,
        String expectedInboundReason,
        OffsetDateTime firstOrderedAt,
        OffsetDateTime lastOrderedAt,
        int backorderAmount
) {}
