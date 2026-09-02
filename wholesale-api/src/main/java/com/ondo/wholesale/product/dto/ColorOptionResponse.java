package com.ondo.wholesale.product.dto;

import java.util.List;

/**
 * 색상별 SKU 묶음. 자체 {@code id}가 없다 — 리스트 key 는 {@code color.id}.
 * 정렬은 서버 보장 — 색상은 그룹 → 색상 순서, {@code variants}는 사이즈 순서.
 */
public record ColorOptionResponse(ColorResponse color, List<VariantResponse> variants) {}
