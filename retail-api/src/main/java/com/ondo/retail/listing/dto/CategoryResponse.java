package com.ondo.retail.listing.dto;

import java.util.List;

/**
 * 쇼핑몰 좌측 네비의 3단 트리.
 *
 * <p>고정 마스터라 자주 안 변한다. 프론트에서 캐시해도 된다.
 * 도매의 응답에는 {@code depth} 가 있지만 여기엔 없다 — 소매는 중첩 구조만 쓴다.
 *
 * @param id       목록의 categoryId 에 넣는 값
 * @param name     카테고리 이름
 * @param children 하위 노드. 리프(depth 3)에는 없다
 */
public record CategoryResponse(Long id, String name, List<CategoryResponse> children) {
}
