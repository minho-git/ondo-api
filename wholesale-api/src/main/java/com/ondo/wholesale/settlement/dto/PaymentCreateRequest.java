package com.ondo.wholesale.settlement.dto;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.settlement.PaidBy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입금 등록 요청 (api-lite/07_정산/POST_payments.md). {@code Idempotency-Key} 헤더 필수 —
 * 입금은 중복 등록을 되돌릴 수단이 없다. "입금만 진행"과 "입금 및 정산"이 같은 엔드포인트고,
 * {@code allocations}를 생략하거나 비우면 선수금이 된다.
 */
public record PaymentCreateRequest(
        Long retailerId,
        Integer amount,
        OffsetDateTime paidAt,
        PaidBy paidBy,
        PaymentMethod method,
        String memo,
        List<PaymentAllocationRequest> allocations
) {

    /** 주문 하나에 배분할 금액. {@code orderId} 중복 불가, {@code amount > 0}. */
    public record PaymentAllocationRequest(Long orderId, Integer amount) {}
}
