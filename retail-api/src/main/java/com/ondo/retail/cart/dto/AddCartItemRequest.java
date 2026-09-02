package com.ondo.retail.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기.
 *
 * @param variantId 상품 상세의 {@code colorOptions[].variants[].id}
 * @param qty       담을 수량. 1 이상
 */
public record AddCartItemRequest(

        @NotNull(message = "상품 옵션을 선택해주세요")
        Long variantId,

        @NotNull(message = "수량을 입력해주세요")
        @Min(value = 1, message = "1장 이상 담을 수 있어요")
        Integer qty) {
}
