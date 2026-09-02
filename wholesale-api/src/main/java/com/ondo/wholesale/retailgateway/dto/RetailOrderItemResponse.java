package com.ondo.wholesale.retailgateway.dto;

/** 생성된 주문 라인. {@code unitPrice}는 주문 시점 스냅샷 — 이후 판매가가 바뀌어도 고정. */
public record RetailOrderItemResponse(Long id, Long variantId, int qty, int unitPrice) {}
