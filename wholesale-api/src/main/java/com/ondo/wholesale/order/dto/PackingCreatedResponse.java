package com.ondo.wholesale.order.dto;

import com.ondo.wholesale.order.PackingStatus;

import java.time.OffsetDateTime;

/** 포장 준비 201 응답 — 생성된 카드 1장. 생성 직후는 항상 {@code READY}, {@code outboundId}는 {@code null}. */
public record PackingCreatedResponse(
        Long id,
        Long orderId,
        PackingStatus status,
        Long outboundId,
        OffsetDateTime createdAt,
        java.util.List<PackingItemResponse> items
) {}
