package com.ondo.wholesale.backorder.dto;

import java.time.LocalDate;

/**
 * 예상 입고일 등록 (PUT — 값 2개를 통째로 대체). {@code expectedInboundDate} 키는 반드시
 * 보내며 {@code null} = 해제(사유도 함께 null 로 덮는다). 과거 날짜를 막지 않는다.
 */
public record ExpectedInboundRequest(LocalDate expectedInboundDate, String expectedInboundReason) {}
