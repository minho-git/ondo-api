package com.ondo.wholesale.backorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.dto.response.PackingItemResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 배분으로 생긴 포장 카드 하나 — 주문마다 한 장. 카드 스키마는 포장 대기열과 같아서
 * 배분 직후 재조회 없이 그대로 그릴 수 있고, 어느 주문 카드인지 {@code orderId}·{@code orderNumber}가 붙는다.
 */
public record AllocationPackingResponse(
        Long id,
        Long orderId,
        Integer orderNumber,
        PackingStatus status,
        Long outboundId,
        @JsonProperty("isCancellable") boolean isCancellable,
        OffsetDateTime createdAt,
        List<PackingItemResponse> items
) {}
