package com.ondo.retail.cart.dto;

/**
 * @param cartItemId 생성되었거나 수량이 더해진 행의 id
 * @param qty        더해진 뒤의 최종 수량
 * @param count      장바구니 종류 수. 헤더 뱃지를 다시 안 부르게 같이 준다
 */
public record AddCartItemResponse(Long cartItemId, int qty, long count) {
}
