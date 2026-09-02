package com.ondo.wholesale.inventory.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입고 등록 201 응답 — 헤더 1건 + 라인(로트) N건.
 * 같은 키 재요청은 200 + 동일 본문(멱등) — 재고가 두 번 오르지 않는다.
 */
public record InboundCreatedResponse(Long id, OffsetDateTime receivedAt, List<InboundItemResponse> items) {}
