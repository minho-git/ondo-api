package com.ondo.wholesale.settlement.dto;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.settlement.PaidBy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입금 등록 201 응답. 같은 키 재요청은 200 + 동일 본문(멱등). {@code unallocatedAmount} =
 * <b>이번 입금</b>에서 어느 주문에도 안 붙은 돈 — 배분이 남은 선수금까지 끌어 써도 음수가 되지 않는다.
 * {@code prepaidRemaining}은 등록 후 거래처 선수금 전체 (MUL-125). {@code ledgerBalance}는 이 입금 반영 후의
 * 거래처 미수 잔액(음수 = 소매처 채무) — 좌측 아코디언 헤더를 재조회 없이 갱신할 수 있다.
 */
public record PaymentCreatedResponse(
        Long id,
        Long retailerId,
        String retailerName,
        int amount,
        OffsetDateTime paidAt,
        PaidBy paidBy,
        PaymentMethod method,
        String memo,
        int unallocatedAmount,
        int prepaidRemaining,
        List<Allocation> allocations,
        int ledgerBalance,
        OffsetDateTime createdAt
) {

    /**
     * 만들어진 배분 하나. {@code orderNumber} 표기(ORD-006)는 프론트 조립. {@code paymentId}는 돈을 꺼낸 입금 —
     * 선수금에서 끌어 쓰면 이번 입금이 아닐 수 있고, 한 주문이 입금 여럿에 걸치면 줄이 여럿이다 (MUL-125).
     */
    public record Allocation(Long id, Long orderId, Integer orderNumber, Long paymentId, int amount,
                             OffsetDateTime createdAt) {}
}
