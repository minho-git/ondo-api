package com.ondo.retail.listing.dto;

import java.util.List;

/**
 * 상품 목록의 검색·필터 조건.
 *
 * <p>쇼핑몰 그리드와 검색 결과가 같은 엔드포인트를 쓴다. {@code q} 가 있으면 검색이다.
 *
 * @param q          검색어. 게시 제목 부분일치. 없으면 전체
 * @param categoryId 하위 카테고리를 포함한다. depth 1·2 를 주면 그 아래 전부
 */
public record ListingSearchCondition(
        String q,
        Long categoryId,
        List<Long> colorIds,
        List<String> sizes,
        Integer priceFrom,
        Integer priceTo) {
}
