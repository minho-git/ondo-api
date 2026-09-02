package com.ondo.wholesale.inventory.dto;

import java.math.BigDecimal;

/** 입고 라인(로트) 하나. {@code unitCost}는 로트별 매입단가 — 라인마다 다를 수 있다. */
public record InboundItemRequest(Long variantId, Integer qty, BigDecimal unitCost) {}
