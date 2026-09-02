package com.ondo.retail.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 주문 접수.
 *
 * @param agentName  사입삼촌. <b>도매처가 여럿이어도 사람은 하나다</b>
 */
public record PlaceOrderRequest(

        @NotEmpty(message = "주문할 상품을 선택해주세요")
        List<Long> cartItemIds,

        String agentName,
        String agentPhone,

        @NotEmpty(message = "도매처별 결제·수령 방법을 선택해주세요")
        @Valid
        List<WholesalerOption> wholesalerOptions) {

    /** 결제 조건과 수령 방법은 도매처마다 고른다. */
    public record WholesalerOption(
            @NotNull Long wholesalerId,
            @NotNull PaymentTerm paymentTerm,
            @NotNull ReceiveMethod receiveMethod) {
    }
}
