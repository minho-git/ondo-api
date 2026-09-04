package com.ondo.wholesale.retailgateway.dto;

/**
 * 옵션 하나의 정보 (MUL-88). 소매 장바구니가 쓴다.
 *
 * <p>소매는 장바구니에 {@code variantId} 와 수량만 저장한다. 상품명·이미지·단가는
 * 전부 도매 것이라 장바구니를 펼칠 때마다 여기로 물어본다.
 *
 * <p><b>게시가 내려간 옵션도 돌려준다.</b> 담아둔 사이 도매가 시즌을 닫아도 소매
 * 장바구니 행은 남아 있어서, 안 돌려주면 소매가 그 줄을 그릴 수 없다.
 * 그 경우 {@code orderable} 이 false 로 오고 소매는 회색으로 그린 뒤 주문에서 뺀다.
 *
 * @param salePrice  게시가 내려갔으면 마지막 값이다
 * @param orderLimit 1회 주문당 최대. {@code 0} 이면 무제한
 * @param orderable  지금 주문할 수 있나. 게시 중(ON_SALE)이고 옵션이 살아 있어야 true
 */
public record RetailVariantInfoResponse(
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
        boolean orderable) {}
