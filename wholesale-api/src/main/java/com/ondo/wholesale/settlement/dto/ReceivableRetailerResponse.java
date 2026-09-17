package com.ondo.wholesale.settlement.dto;

import java.time.OffsetDateTime;

/**
 * 정산 탭 아코디언 헤더 한 행 = 소매처 하나. {@code ledgerBalance}는 부호 그대로 —
 * 음수 = 소매처 채무(화면은 양수로 읽어 "미수 잔액"), 양수 = 선수금(표기를 뒤집으면 안 된다).
 * {@code orderCount}는 확정 주문 수. 확정 주문도 오간 돈도 없는 소매처는 목록에 없다.
 * {@code retailerCode}는 출처가 아직 없어 null, {@code lastOccurredAt}은 원장이 비었으면 null (MUL-126).
 */
public record ReceivableRetailerResponse(
        Long retailerId,
        String retailerCode,
        String retailerName,
        int orderCount,
        int ledgerBalance,
        OffsetDateTime lastOccurredAt
) {}
