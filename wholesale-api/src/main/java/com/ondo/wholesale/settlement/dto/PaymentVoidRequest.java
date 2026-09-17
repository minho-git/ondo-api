package com.ondo.wholesale.settlement.dto;

/** 입금 취소 요청 (MUL-127). 사유는 필수 — 돈 기록을 되돌린 이유가 남아야 한다. 200자 이하. */
public record PaymentVoidRequest(String reason) {}
