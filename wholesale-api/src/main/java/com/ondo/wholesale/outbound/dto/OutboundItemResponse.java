package com.ondo.wholesale.outbound.dto;

import com.ondo.wholesale.product.Size;

/** 출고 상세의 SKU 행 — 주문을 구분하지 않고 SKU 단위로 합친다. */
public record OutboundItemResponse(
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        String productName,
        String color,
        Size size,
        int qty
) {}
