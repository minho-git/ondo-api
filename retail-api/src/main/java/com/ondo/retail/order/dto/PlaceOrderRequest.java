package com.ondo.retail.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 주문 접수.
 *
 * @param cartItemIds       주문할 장바구니 줄. 주문서에서 받은 cartItemId 를 그대로 넘긴다
 * @param agentName         사입삼촌. <b>도매처가 여럿이어도 사람은 하나다</b>
 * @param agentPhone        사입삼촌 연락처
 * @param wholesalerOptions 도매처별 결제 조건과 수령 방법
 */
public record PlaceOrderRequest(

        @NotEmpty(message = "주문할 상품을 선택해주세요")
        List<Long> cartItemIds,

        String agentName,
        String agentPhone,

        @NotEmpty(message = "도매처별 결제·수령 방법을 선택해주세요")
        @Valid
        List<WholesalerOption> wholesalerOptions) {


    /**
     * 결제 조건과 수령 방법은 도매처마다 고른다.
     *
     * @param wholesalerId  어느 도매처에 대한 설정인지
     * @param paymentTerm   CASH(현금) · BANK_TRANSFER(계좌이체). 계좌 미등록 도매처는 현금만 된다
     * @param receiveMethod RETAILER(직접 수령) · AGENT(사입삼촌)
     */
    public record WholesalerOption(
            @NotNull Long wholesalerId,
            @NotNull PaymentTerm paymentTerm,
            @NotNull ReceiveMethod receiveMethod) {
    }
}
