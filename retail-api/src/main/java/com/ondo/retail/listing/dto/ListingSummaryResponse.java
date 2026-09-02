package com.ondo.retail.listing.dto;

/**
 * 상품 목록의 카드 하나.
 *
 * <p><b>깊이 2로 끊는다.</b> 색상·사이즈 목록도 재고도 안 내린다 — 카드가 안 쓴다.
 * 재고를 안 내리는 건 의도다. 접수는 재고를 보지 않고 모자라면 미송으로 잡혀서
 * 목록에 "품절" 이라는 상태가 없다.
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

    /** 목록에서는 도매처 id·상호만 쓴다. 매장 위치는 상세에서 준다. */
    public record WholesalerBrief(Long id, String name) {
    }
}
