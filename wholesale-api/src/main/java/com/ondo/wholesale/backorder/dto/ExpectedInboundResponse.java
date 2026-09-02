package com.ondo.wholesale.backorder.dto;

import java.time.LocalDate;

/** 저장된 예상 입고일. 이력은 남지 않는다 — 최신값 하나뿐. */
public record ExpectedInboundResponse(
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        LocalDate expectedInboundDate,
        String expectedInboundReason
) {}
