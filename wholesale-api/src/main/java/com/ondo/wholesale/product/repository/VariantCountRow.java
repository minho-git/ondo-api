package com.ondo.wholesale.product.repository;

/** 상품 목록 집계 한 행 — 살아있는 variant 기준 색 수·SKU 수. */
public record VariantCountRow(Long productId, long colorCount, long variantCount) {}
