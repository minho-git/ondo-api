package com.ondo.retail.listing.dto;

import java.util.List;

/**
 * 필터 사이드바를 그리는 값 전부.
 *
 * <p>목록과 따로 부른다 — 필터를 바꿔도 선택지 자체는 안 바뀐다.
 * 목록 응답에 실으면 매번 같은 걸 다시 받는다.
 */
public record FilterOptionsResponse(
        List<ColorGroup> colorGroups,
        List<String> sizes,
        PriceRange priceRange) {

    public record ColorGroup(Long id, String name, List<Color> colors) {
    }

    /** @param id 목록의 {@code colorIds} 에 넣는 값 */
    public record Color(Long id, String name, String hex) {
    }

    /** 게시 중인 상품 전체의 실제 최저·최고가. 가격 슬라이더의 양 끝이다. */
    public record PriceRange(Integer min, Integer max) {
    }
}
