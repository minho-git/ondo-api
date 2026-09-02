package com.ondo.wholesale.product.dto;

import com.ondo.wholesale.product.Size;

/**
 * variant 하나의 판매가·주문 제한.
 *
 * <p>variant 지정은 두 방식 중 하나만 쓴다 — 기존 variant 는 {@code variantId},
 * 아직 id 가 없는 신규 variant 는 {@code (colorId, size)}. 등록(POST)은 전부 신규라
 * 항상 후자다. 둘 다 채우거나 둘 다 비우면 400 (PATCH 계약서).
 */
public record VariantPriceRequest(Long variantId, Long colorId, Size size, Integer salePrice, Integer orderLimit) {}
