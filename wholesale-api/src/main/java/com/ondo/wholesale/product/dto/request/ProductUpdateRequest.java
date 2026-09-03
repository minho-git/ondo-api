package com.ondo.wholesale.product.dto.request;

import java.util.List;

/**
 * 상품 수정 요청 (api-lite/02_상품게시/PATCH_products_{productId}.md).
 *
 * <p>PATCH 의미론 — 생략 = 무변경, {@code colorOptions}·{@code listing.images}·
 * {@code listing.variantPrices}는 전체 교체, 이 요청의 모든 최상위 필드는 {@code null} 불가(400).
 * {@code colorOptions}는 전체 상태를 보내면 서버가 현재와 대조해 diff 를 만든다.
 * 게시글이 없으면 {@code listing}은 생성 분기를 탄다 (응답은 200).
 */
public record ProductUpdateRequest(
        String name,
        Long categoryId,
        List<ColorOptionRequest> colorOptions,
        ListingUpsertRequest listing
) {}
