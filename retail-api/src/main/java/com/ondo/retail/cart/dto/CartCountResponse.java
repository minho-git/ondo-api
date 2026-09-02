package com.ondo.retail.cart.dto;

/**
 * 헤더 뱃지용. 담은 수량이 아니라 종류 수다.
 *
 * @param count 담은 종류 수. 같은 상품이라도 색상·사이즈가 다르면 따로 센다
 */
public record CartCountResponse(long count) {
}
