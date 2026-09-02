package com.ondo.wholesale.backorder.dto;

import java.time.OffsetDateTime;

/**
 * SKU 를 기다리는 미송 하나. 화면의 "미송 수량" 컬럼은 {@code qty}(원래 미송량, 불변)가
 * 아니라 {@code remainingQty}다. {@code elapsedDays}는 미송 발생({@code createdAt}) 기준 —
 * 주문 시각 기준이 아니다. {@code unitPrice}는 주문 시점 스냅샷.
 */
public record BackorderResponse(
        Long id,
        Long orderId,
        Integer orderNumber,
        Long orderItemId,
        OffsetDateTime orderedAt,
        OffsetDateTime createdAt,
        int elapsedDays,
        Long retailerId,
        String retailerName,
        int qty,
        int remainingQty,
        int unitPrice
) {}
