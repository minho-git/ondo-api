package com.ondo.wholesale.retailgateway.dto;

import com.ondo.wholesale.order.OrderStatusKey;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 생성 201 응답 (api/08_소매접점.md §2.2). {@code status}는 항상 {@code NEW} —
 * 확정은 도매 사장님이 ERP 에서 한다. {@code orderNumber}는 도매처별 연번.
 */
public record RetailOrderCreatedResponse(
        Long id,
        Integer orderNumber,
        Long retailOrderId,
        OrderStatusKey status,
        int orderAmount,
        OffsetDateTime orderedAt,
        List<RetailOrderItemResponse> items
) {}
