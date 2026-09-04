package com.ondo.wholesale.retailgateway.dto;

import java.util.List;

/**
 * 소매 쇼핑몰 좌측 네비의 3단 트리 (MUL-88).
 *
 * <p>도매 화면용 {@code CategoryNodeResponse} 에는 {@code depth} 가 있지만 여기엔 없다.
 * 소매는 중첩 구조만 쓴다.
 *
 * @param children 하위 노드. 리프(depth 3)는 빈 배열이다
 */
public record RetailCategoryResponse(Long id, String name, List<RetailCategoryResponse> children) {}
