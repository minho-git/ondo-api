package com.ondo.wholesale.inventory.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입고 등록 요청 (api-lite/03_재고/POST_inbounds.md). {@code Idempotency-Key} 헤더 필수.
 *
 * <p>같은 {@code variantId}를 여러 줄에 넣을 수 있다 — 단가가 다르면 다른 로트다.
 * {@code (variantId, unitCost)}가 완전히 같은 중복만 거절(400 DUPLICATE_LOT).
 */
public record InboundCreateRequest(OffsetDateTime receivedAt, List<InboundItemRequest> items) {}
