package com.ondo.wholesale.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 계좌 등록 요청. 은행 목록은 프론트 상수(서버 조회 API 없음), 계좌번호 형식은 검증하지 않는다.
 * 첫 계좌는 {@code isPrimary} 값과 무관하게 서버가 주계좌로 만든다.
 */
public record BankAccountCreateRequest(
        String bankName,
        String accountNo,
        String accountHolder,
        String memo,
        @JsonProperty("isPrimary") Boolean isPrimary
) {}
