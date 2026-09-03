package com.ondo.wholesale.product.dto;

import com.ondo.wholesale.master.dto.CategoryPathItem;

import java.util.List;

/**
 * 상품 상세 (api-lite/02_상품게시/GET_products_{productId}.md). 등록·수정 응답도 이 스키마다.
 *
 * <p>{@code productNumber}는 최상위에 한 번만 — SKU 표시 코드(`SU-18-1`)는 프론트가
 * {@code variantNumber}와 조합해 만든다. {@code listing}이 없으면 필드 생략이 아니라 {@code null}.
 */
public record ProductDetailResponse(
        Long id,
        Integer productNumber,
        String name,
        List<CategoryPathItem> categoryPath,
        List<ColorOptionResponse> colorOptions,
        ListingResponse listing
) {}
