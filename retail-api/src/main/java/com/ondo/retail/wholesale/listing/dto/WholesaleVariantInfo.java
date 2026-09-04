package com.ondo.retail.wholesale.listing.dto;

/**
 * 도매가 주는 옵션 정보 (MUL-88). 소매 장바구니가 쓴다.
 *
 * <p>게시가 내려간 옵션도 온다 — 그때는 {@code orderable} 이 false 다.
 * 게시 자체가 없는 옵션은 응답에 아예 안 들어온다.
 */
public record WholesaleVariantInfo(
        Long variantId,
        Long listingId,
        String title,
        String thumbnailUrl,
        String colorName,
        String size,
        Integer salePrice,
        Integer orderLimit,
        Long wholesalerId,
        String wholesalerName,
        boolean orderable) {}
