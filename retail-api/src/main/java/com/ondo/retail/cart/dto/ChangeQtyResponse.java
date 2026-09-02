package com.ondo.retail.cart.dto;

/**
 * 수량 변경 결과.
 *
 * <p>바뀐 줄 금액을 같이 준다. 안 주면 프론트가 숫자 하나 바꾸자고 장바구니를 통째로
 * 다시 불러야 한다.
 *
 * @param cartItemId 바뀐 항목 id
 * @param qty        바뀐 뒤의 수량
 * @param lineAmount qty × salePrice. 도매가 옵션을 내렸으면 단가가 없어서 null 이다
 */
public record ChangeQtyResponse(Long cartItemId, int qty, Integer lineAmount) {
}
