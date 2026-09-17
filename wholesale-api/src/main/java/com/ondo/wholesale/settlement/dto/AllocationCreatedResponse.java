package com.ondo.wholesale.settlement.dto;

import java.util.List;

/**
 * 선수금으로 정산 201 응답 (MUL-125). 원장은 안 바뀌므로 {@code ledgerBalance}가 없다.
 * {@code allocations}는 오래된 입금부터 꺼낸 순서다. {@code prepaidRemaining}은 정산 후 거래처 선수금.
 */
public record AllocationCreatedResponse(
        Long retailerId,
        String retailerName,
        List<PaymentCreatedResponse.Allocation> allocations,
        int prepaidRemaining
) {}
