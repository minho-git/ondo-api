package com.ondo.wholesale.retailgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 소매 필터 사이드바를 그리는 값 전부 (MUL-88).
 *
 * <p>목록과 따로 부른다 — 필터를 바꿔도 선택지 자체는 안 바뀐다.
 *
 * <p>색상과 사이즈는 <b>마스터 전체</b>다. 지금 파는 상품에 없는 색도 들어간다.
 * 선택지가 필터 결과에 따라 늘었다 줄었다 하면 화면이 흔들려서다 — 아무 상품도 없는
 * 색을 고르면 목록이 0건으로 나오는 게 맞다.
 * 가격만 예외로 <b>게시 중인 상품의 실제 최저·최고가</b>다. 슬라이더 양 끝이라
 * 아무도 안 파는 구간까지 늘려 놓으면 손잡이를 끝까지 끌어야 한다.
 */
public record RetailFilterOptionsResponse(
        List<ColorGroup> colorGroups,
        List<String> sizes,
        PriceRange priceRange) {

    @Schema(name = "RetailGatewayColorGroup")
    public record ColorGroup(Long id, String name, List<Color> colors) {}

    /** @param hex #RRGGBB. 색 동그라미를 그리는 값 */
    @Schema(name = "RetailGatewayFilterColor")
    public record Color(Long id, String name, String hex) {}

    /** 게시 중인 상품이 하나도 없으면 둘 다 null 이다. */
    @Schema(name = "RetailGatewayPriceRange")
    public record PriceRange(Integer min, Integer max) {}
}
