package com.ondo.wholesale.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 계좌 수정 요청 — 보낸 필드만 바뀐다(공통 §6). {@code memo}만 null 로 지울 수 있고
 * 나머지 필드의 null 은 400. 주계좌를 {@code isPrimary: false}로 직접 해제할 수 없다
 * (400 PRIMARY_ACCOUNT_CANNOT_BE_UNSET) — 다른 계좌를 승격하면 부수 효과로 내려간다.
 */
public record BankAccountUpdateRequest(
        String bankName,
        String accountNo,
        String accountHolder,
        String memo,
        @JsonProperty("isPrimary") Boolean isPrimary
) {}
