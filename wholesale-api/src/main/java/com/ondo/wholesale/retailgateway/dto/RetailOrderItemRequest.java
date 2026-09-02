package com.ondo.wholesale.retailgateway.dto;

/**
 * 소매 주문 라인. {@code expectedUnitPrice}는 소매 화면이 소매처에게 보여준 판매가 —
 * 서버가 현재 판매가와 대조해 어긋나면 409 PRICE_CHANGED 로 되돌린다.
 */
public record RetailOrderItemRequest(Long variantId, Integer qty, Integer expectedUnitPrice) {}
