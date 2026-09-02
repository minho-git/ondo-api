package com.ondo.retail.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 상품 상세. 색상 · 사이즈 · 판매가를 한 번에 내린다.
 *
 * <p>게시 내린 옵션은 아예 안 나온다. {@code colorOptions} 와 {@code variants} 는
 * 둘 다 게시된 것만이고, {@code totalVariantCount} 로 "전체 15개 중 5개" 를 보여준다.
 *
 * <p>정렬은 서버가 보장한다 — 색상은 그룹 → 색상 순, 사이즈 순, 이미지는 sortOrder ASC.
 *
 * @param listingId            상품 id
 * @param title                상품명
 * @param description          도매가 적은 상품 설명
 * @param productNumber        도매처가 매기는 품번. 매장에서 이걸로 찾는다
 * @param isSinglePieceAllowed 낱장으로 살 수 있는지
 * @param minSalePrice         옵션 중 최저가
 * @param maxSalePrice         옵션 중 최고가
 * @param listedVariantCount   지금 살 수 있는 옵션 수
 * @param totalVariantCount    원래 옵션 수. \"전체 15개 중 5개\" 로 그린다
 * @param categoryPath         루트에서 리프까지. 항상 3단이다
 * @param wholesaler           파는 도매처
 * @param colorOptions         색상별 묶음. 각 색상 아래에 사이즈가 온다
 * @param images               sortOrder 오름차순으로 정렬돼 온다. 다시 정렬할 필요 없다
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


    /**
     * 루트 → 리프. 항상 3단이다.
     *
     * @param id   카테고리 id
     * @param name 카테고리 이름
     */
    public record CategoryNode(Long id, String name) {
    }

    /**
     * 매장 위치를 같이 준다 — 방문 수령을 고를 수 있어서다.
     *
     * @param id            도매처 id
     * @param name          도매처 상호
     * @param storeBuilding 매장 건물. 청평화패션몰 같은 것
     * @param storeUnit     매장 호수. 2층 24호 같은 것
     */
    @Schema(name = "ListingWholesaler")
    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit) {
    }
    /**
     * @param color    색상 정보
     * @param imageUrl 이 색상의 대표 이미지
     * @param variants 이 색상으로 고를 수 있는 사이즈들
     */

    public record ColorOption(Color color, String imageUrl, List<Variant> variants) {
    }
    /**
     * @param id        필터의 colorIds 에 넣는 값
     * @param name      색상 이름
     * @param hex       #RRGGBB. 색 동그라미를 그리는 값
     * @param groupName 필터의 색상 그룹 이름
     */
    @Schema(name = "ListingColor")
    public record Color(Long id, String name, String hex, String groupName) {
    }

    /**
     * @param id         장바구니에 담을 때 보내는 값
     * @param size       사이즈
     * @param salePrice  이 옵션의 판매가
     * @param orderLimit 1회 주문당 최대. {@code 0} 이면 무제한
     */
    public record Variant(Long id, String size, Integer salePrice, Integer orderLimit) {
    }
    /**
     * @param id        이미지 id
     * @param url       이미지 주소
     * @param sortOrder 서버가 이미 이 순서로 정렬해서 준다
     */

    public record Image(Long id, String url, Integer sortOrder) {
    }
}
