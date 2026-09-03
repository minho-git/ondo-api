package com.ondo.wholesale.order.dto;

import com.ondo.wholesale.product.domain.Size;

/** 포장 카드 안의 항목 하나. {@code qty}는 배분 수량 — 출고 수량이 아니다. */
public record PackingItemResponse(
        Long id,
        Long orderItemId,
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        String productName,
        String color,
        Size size,
        int qty
) {}
