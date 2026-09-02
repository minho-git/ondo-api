package com.ondo.retail.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 수량 변경. 더하는 게 아니라 그 값으로 교체한다. */
public record ChangeQtyRequest(

        @NotNull(message = "수량을 입력해주세요")
        @Min(value = 1, message = "1장 이상이어야 해요")
        Integer qty) {
}
