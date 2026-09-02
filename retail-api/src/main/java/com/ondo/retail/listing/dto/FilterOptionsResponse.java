package com.ondo.retail.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 필터 사이드바를 그리는 값 전부.
 *
 * <p>목록과 따로 부른다 — 필터를 바꿔도 선택지 자체는 안 바뀐다.
 * 목록 응답에 실으면 매번 같은 걸 다시 받는다.
 *
 * @param colorGroups 색상 그룹별로 묶인 색상들
 * @param sizes       고를 수 있는 사이즈 전부
 * @param priceRange  가격 슬라이더의 양 끝
 */
public record FilterOptionsResponse(
        List<ColorGroup> colorGroups,
        List<String> sizes,
        PriceRange priceRange) {

    /**
     * @param id     색상 그룹 id
     * @param name   그룹 이름. 빨강 계열 · 파랑 계열 같은 것
     * @param colors 이 그룹에 속한 색상들
     */

    public record ColorGroup(Long id, String name, List<Color> colors) {
    }

    /**
     * @param id   목록의 {@code colorIds} 에 넣는 값
     * @param name 색상 이름
     * @param hex  #RRGGBB
     */
    @Schema(name = "FilterColor")
    public record Color(Long id, String name, String hex) {
    }

    /**
     * 게시 중인 상품 전체의 실제 최저·최고가. 가격 슬라이더의 양 끝이다.
     *
     * @param min 실제 최저가
     * @param max 실제 최고가
     */
    public record PriceRange(Integer min, Integer max) {
    }
}
