package com.ondo.wholesale.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 정산 계좌 한 건. {@code accountNo}는 저장된 문자열 그대로(- 포함, 서버 정규화 없음).
 * 주계좌는 최대 1개, 정렬은 주계좌 먼저 → 등록순 고정(sort 미지원).
 */
public record BankAccountResponse(
        Long id,
        String bankName,
        String accountNo,
        String accountHolder,
        String memo,
        @JsonProperty("isPrimary") boolean isPrimary,
        OffsetDateTime createdAt
) {}
