package com.ondo.retail.listing.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상품 상세. 색상 · 사이즈 · 판매가를 한 번에 내린다.
 *
 * <p>게시 내린 옵션은 아예 안 나온다. {@code colorOptions} 와 {@code variants} 는
 * 둘 다 게시된 것만이고, {@code totalVariantCount} 로 "전체 15개 중 5개" 를 보여준다.
 *
 * <p>정렬은 서버가 보장한다 — 색상은 그룹 → 색상 순, 사이즈 순, 이미지는 sortOrder ASC.
 */
public record ListingDetailResponse(
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

    /** 루트 → 리프. 항상 3단이다. */
    public record CategoryNode(Long id, String name) {
    }

    /** 매장 위치를 같이 준다 — 방문 수령을 고를 수 있어서다. */
    @Schema(name = "ListingWholesaler")
    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit) {
    }

    public record ColorOption(Color color, String imageUrl, List<Variant> variants) {
    }

    @Schema(name = "ListingColor")

    public record Color(Long id, String name, String hex, String groupName) {
    }

    /**
     * @param id         장바구니에 담을 때 보내는 값
     * @param orderLimit 1회 주문당 최대. {@code 0} 이면 무제한
     */
    public record Variant(Long id, String size, Integer salePrice, Integer orderLimit) {
    }

    public record Image(Long id, String url, Integer sortOrder) {
    }
}
