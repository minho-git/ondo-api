package com.ondo.retail.listing.dto;

/**
 * 상품 목록의 카드 하나.
 *
 * <p><b>깊이 2로 끊는다.</b> 색상·사이즈 목록도 재고도 안 내린다 — 카드가 안 쓴다.
 * 재고를 안 내리는 건 의도다. 접수는 재고를 보지 않고 모자라면 미송으로 잡혀서
 * 목록에 "품절" 이라는 상태가 없다.
 *
 * @param listingId            상품 상세를 부를 때 쓴다
 * @param title                상품명
 * @param wholesaler           파는 도매처
 * @param thumbnailUrl         대표 이미지
 * @param minSalePrice         옵션 중 제일 싼 값. 카드에 \"12,500원~\" 으로 그린다
 * @param colorCount           고를 수 있는 색상 가짓수
 * @param sizeCount            고를 수 있는 사이즈 가짓수
 * @param isSinglePieceAllowed 낱장으로 살 수 있는지. false 면 묶음 단위로만 산다
 */
public record ListingSummaryResponse(
        Long listingId,
        String title,
        WholesalerBrief wholesaler,
        String thumbnailUrl,
        Integer minSalePrice,
        Integer colorCount,
        Integer sizeCount,
        Boolean isSinglePieceAllowed) {


    /**
     * 목록에서는 도매처 id·상호만 쓴다. 매장 위치는 상세에서 준다.
     *
     * @param id   도매처 id
     * @param name 도매처 상호
     */
    public record WholesalerBrief(Long id, String name) {
    }
}
