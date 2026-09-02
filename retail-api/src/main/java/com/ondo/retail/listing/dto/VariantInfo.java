package com.ondo.retail.listing.dto;

/**
 * 옵션 하나의 정보. 장바구니가 쓴다.
 *
 * <p>장바구니에는 {@code variantId} 와 수량만 저장한다. 상품명 · 이미지 · 단가는
 * 전부 도매 것이라 조회할 때마다 가져온다. <b>담아둔 사이에 도매가 가격을 올렸으면
 * 장바구니에서 바뀌어 보인다.</b>
 *
 * @param orderable  지금 주문할 수 있나. 담아둔 사이에 시즌이 끝나거나 옵션이 지워질 수 있다.
 *                   그때도 장바구니 행은 남으므로 회색으로 그리고 주문에서 뺀다
 * @param salePrice  {@code orderable} 이 false 면 마지막 값이거나 null 이다
 * @param orderLimit 1회 주문당 최대. {@code 0} 이면 무제한
 */
public record VariantInfo(
        Long variantId,
        Long listingId,
        String title,
        String thumbnailUrl,
        String colorName,
        String size,
        Integer salePrice,
        Integer orderLimit,
        Long wholesalerId,
        String wholesalerName,
        boolean orderable) {
}
