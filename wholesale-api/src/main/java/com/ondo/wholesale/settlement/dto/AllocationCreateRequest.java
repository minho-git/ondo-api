package com.ondo.wholesale.settlement.dto;

import java.util.List;

/**
 * 선수금으로 정산 요청 (MUL-125) — 새 입금 없이 받아 둔 돈을 출고된 주문에 붙인다.
 * 주문별 줄의 규칙은 입금 등록의 배분과 같다({@code orderId} 중복 불가, {@code amount > 0}).
 */
public record AllocationCreateRequest(
        Long retailerId,
        List<PaymentCreateRequest.PaymentAllocationRequest> allocations
) {}
