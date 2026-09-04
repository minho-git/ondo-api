package com.ondo.wholesale.retailgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 소매 상품 상세 (MUL-88). 색상·사이즈·판매가를 한 번에 내린다.
 *
 * <p>게시된 옵션만 나온다. 그래서 {@code totalVariantCount} 를 같이 준다 —
 * 소매가 "전체 15개 중 5개" 라고 그려야 도매가 안 올린 색을 손님이 안 기다린다.
 *
 * <p><b>정렬은 여기서 끝낸다.</b> 색상은 그룹 순 → 그룹 안 순서, 사이즈는 XS~FREE 순,
 * 이미지는 sortOrder 순으로 이미 정렬해서 준다. 소매가 다시 정렬하지 않는다.
 *
 * @param productNumber      도매처가 매기는 품번. 매장에서 이걸로 찾는다
 * @param minSalePrice       옵션 중 최저가
 * @param maxSalePrice       옵션 중 최고가
 * @param listedVariantCount 지금 살 수 있는 옵션 수
 * @param totalVariantCount  상품이 가진 옵션 수. 게시 안 한 것까지 센다
 * @param categoryPath       루트에서 리프까지. 항상 3단이다
 */
public record RetailListingDetailResponse(
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
    @Schema(name = "RetailGatewayCategoryNode")
    public record CategoryNode(Long id, String name) {}

    /**
     * 매장 위치를 같이 준다 — 소매가 방문 수령을 고를 수 있어서다.
     *
     * @param storeBuilding 청평화패션몰 같은 것
     * @param storeUnit     2층 24호 같은 것
     */
    @Schema(name = "RetailGatewayListingWholesaler")
    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit) {}

    /**
     * 색상 하나와 그 색으로 고를 수 있는 사이즈들.
     *
     * <p><b>색상별 대표 이미지는 없다.</b> 도매 상품 등록이 옵션별 이미지를 받지 않기로
     * 정해져서(팀 합의) 채울 경로가 없다. 사진은 게시글 이미지({@code images})가 전부다.
     */
    @Schema(name = "RetailGatewayColorOption")
    public record ColorOption(Color color, List<Variant> variants) {}

    /** @param groupName 필터의 색상 그룹 이름 */
    @Schema(name = "RetailGatewayListingColor")
    public record Color(Long id, String name, String hex, String groupName) {}

    /**
     * @param id         소매가 장바구니에 담을 때 보내는 값
     * @param orderLimit 1회 주문당 최대. {@code 0} 이면 무제한
     */
    @Schema(name = "RetailGatewayVariant")
    public record Variant(Long id, String size, Integer salePrice, Integer orderLimit) {}

    /** @param sortOrder 0 이 대표다. 이미 이 순서로 정렬해서 준다 */
    @Schema(name = "RetailGatewayListingImage")
    public record Image(Long id, String url, Integer sortOrder) {}
}
