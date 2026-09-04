package com.ondo.retail.wholesale.listing.dto;

import java.util.List;

/**
 * 도매가 주는 상품 상세 (MUL-88).
 *
 * <p>정렬은 도매가 끝내서 준다 — 색상은 그룹 순, 사이즈는 XS~FREE 순, 이미지는 sortOrder 순.
 * 소매는 순서를 안 건드린다.
 */
public record WholesaleListingDetail(
        Long listingId,
        String title,
        String description,
        Integer productNumber,
        Boolean isSinglePieceAllowed,
        Integer minSalePrice,
        Integer maxSalePrice,
        Integer listedVariantCount,
        Integer totalVariantCount,
        List<CategoryNode> categoryPath,
        Wholesaler wholesaler,
        List<ColorOption> colorOptions,
        List<Image> images) {

    public record CategoryNode(Long id, String name) {}

    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit) {}

    public record ColorOption(Color color, String imageUrl, List<Variant> variants) {}

    public record Color(Long id, String name, String hex, String groupName) {}

    public record Variant(Long id, String size, Integer salePrice, Integer orderLimit) {}

    public record Image(Long id, String url, Integer sortOrder) {}
}
