package com.ondo.retail.wholesale.listing.dto;

/**
 * 도매가 주는 상품 카드 한 장 (MUL-88).
 *
 * <p>소매 {@code ListingSummaryResponse} 와 지금은 필드가 같지만 <b>같은 타입이 아니다.</b>
 * 여기는 도매와의 약속이고 그쪽은 프론트와의 약속이다. 도매가 필드를 바꾸면 이 record 가
 * 먼저 깨지고, 어댑터가 컴파일 에러로 잡아준다 — 프론트 계약까지 조용히 흘러가지 않는다.
 */
public record WholesaleListingSummary(
        Long listingId,
        String title,
        Wholesaler wholesaler,
        String thumbnailUrl,
        Integer minSalePrice,
        Integer colorCount,
        Integer sizeCount,
        Boolean isSinglePieceAllowed) {

    public record Wholesaler(Long id, String name) {}
}
