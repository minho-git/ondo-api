package com.ondo.wholesale.settlement.dto;

import java.time.OffsetDateTime;

/**
 * 배분 취소 200 응답 (MUL-127). 원장은 안 바뀌어 잔액이 없다. 취소한 금액은 선수금으로 돌아가므로
 * {@code prepaidRemaining}(취소 후 거래처 선수금)을 같이 준다.
 */
public record AllocationCancelledResponse(
        Long allocationId,
        Long orderId,
        Long paymentId,
        int amount,
        OffsetDateTime cancelledAt,
        int prepaidRemaining
) {}
