package com.ondo.wholesale.backorder.dto;

import com.ondo.wholesale.product.Size;

import java.time.LocalDate;

/**
 * 미송 탭 아코디언 헤더 한 행 = SKU 하나 (api-lite/05_미송/GET_backorders_variants.md).
 * 미송이 남은({@code backorderQty > 0}) SKU 만 나온다. {@code availableQty}는 화면 헤더에
 * 없지만 담는다 — 펼치기 전에 배분 가능 여부를 알아야 하고 펼침 응답과 일치해야 한다.
 */
public record BackorderSkuResponse(
        Long variantId,
        Long productId,
        Integer productNumber,
        Integer variantNumber,
        String productName,
        String color,
        Size size,
        int backorderQty,
        int availableQty,
        LocalDate expectedInboundDate
) {}
