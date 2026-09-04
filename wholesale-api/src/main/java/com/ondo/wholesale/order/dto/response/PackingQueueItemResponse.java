package com.ondo.wholesale.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.order.PackingStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 포장 대기열의 카드 하나 (api-lite/04_주문/GET_orders_{orderId}_packings.md).
 * 삭제 버튼 활성 조건은 {@code isCancellable} 그대로 쓴다 — 프론트가
 * {@code status}·{@code outboundId} 조합을 다시 계산하지 않는다.
 */
public record PackingQueueItemResponse(
        Long id,
        PackingStatus status,
        Long outboundId,
        @JsonProperty("isCancellable") boolean isCancellable,
        OffsetDateTime createdAt,
        List<PackingItemResponse> items
) {}
