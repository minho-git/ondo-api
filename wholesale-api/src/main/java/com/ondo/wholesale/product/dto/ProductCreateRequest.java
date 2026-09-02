package com.ondo.wholesale.product.dto;

import java.util.List;

/**
 * 상품 등록 요청 (api-lite/02_상품게시/POST_products.md).
 *
 * <p>{@code categoryId}는 리프(depth 3)만 허용. {@code listing: null}은
 * "게시글 없이 상품만 등록"이라는 정상 값이다.
 */
public record ProductCreateRequest(
        String name,
        Long categoryId,
        List<ColorOptionRequest> colorOptions,
        ListingUpsertRequest listing
) {}
