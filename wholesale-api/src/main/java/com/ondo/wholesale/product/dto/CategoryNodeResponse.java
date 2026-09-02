package com.ondo.wholesale.product.dto;

import java.util.List;

/**
 * 카테고리 트리 노드 (api-lite/02_상품게시/GET_categories.md). depth 1~3 전체를 한 번에 내린다.
 *
 * <p>상품에 지정할 수 있는 것은 {@code depth: 3} 리프뿐이고, 목록 필터의 {@code categoryId}는
 * 반대로 상위 노드를 받는다. {@code children} 없으면 {@code []} ({@code null} 아님).
 */
public record CategoryNodeResponse(Long id, String name, int depth, List<CategoryNodeResponse> children) {}
