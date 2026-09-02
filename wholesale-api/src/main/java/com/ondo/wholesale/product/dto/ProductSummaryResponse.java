package com.ondo.wholesale.product.dto;

import com.ondo.wholesale.product.ListingStatus;

import java.util.List;

/**
 * 상품 목록 한 행 — 아코디언 헤더 정보만. 펼침(색상·사이즈·재고 표)은 상세를 따로 부른다.
 * {@code listingStatus}는 게시글이 없으면 {@code null}.
 */
public record ProductSummaryResponse(
        Long id,
        Integer productNumber,
        String name,
        List<CategoryPathItem> categoryPath,
        ListingStatus listingStatus,
        int colorCount,
        int variantCount
) {}
