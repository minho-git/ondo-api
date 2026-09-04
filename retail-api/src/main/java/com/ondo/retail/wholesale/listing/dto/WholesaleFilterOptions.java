package com.ondo.retail.wholesale.listing.dto;

import java.util.List;

/** 도매가 주는 필터 선택지 (MUL-88). */
public record WholesaleFilterOptions(
        List<ColorGroup> colorGroups,
        List<String> sizes,
        PriceRange priceRange) {

    public record ColorGroup(Long id, String name, List<Color> colors) {}

    public record Color(Long id, String name, String hex) {}

    /** 게시 중인 상품이 하나도 없으면 둘 다 null 로 온다. */
    public record PriceRange(Integer min, Integer max) {}
}
