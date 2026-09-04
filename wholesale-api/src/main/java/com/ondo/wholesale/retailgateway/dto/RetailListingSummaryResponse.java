package com.ondo.wholesale.retailgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소매 상품 목록의 카드 하나 (MUL-88).
 *
 * <p>깊이 2로 끊는다 — 색상·사이즈 목록도 재고도 안 내린다. 카드가 안 쓴다.
 * 재고를 빼는 건 의도다. 접수는 재고를 보지 않고 모자라면 미송으로 잡혀서
 * 목록에 "품절" 이라는 상태가 없다.
 *
 * @param minSalePrice 옵션 중 제일 싼 값. 필터를 걸어도 <b>전체 옵션 기준</b>이다 —
 *                     가격 필터 때문에 카드에 적힌 "~부터" 가 달라 보이면 안 된다
 * @param colorCount   게시된 옵션의 색상 가짓수
 * @param sizeCount    게시된 옵션의 사이즈 가짓수
 */
public record RetailListingSummaryResponse(
        Long listingId,
        String title,
        Wholesaler wholesaler,
        String thumbnailUrl,
        Integer minSalePrice,
        Integer colorCount,
        Integer sizeCount,
        Boolean isSinglePieceAllowed) {

    /** 목록에서는 id·상호만 쓴다. 매장 위치는 상세에서 준다. */
    @Schema(name = "RetailGatewayWholesalerBrief")
    public record Wholesaler(Long id, String name) {}
}
