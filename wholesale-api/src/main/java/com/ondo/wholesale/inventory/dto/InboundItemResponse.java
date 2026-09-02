package com.ondo.wholesale.inventory.dto;

import java.math.BigDecimal;

/**
 * 생성된 로트 하나. {@code qtyAfter}·{@code avgCostAfter}는 이 라인 반영 직후 값이라
 * 라인마다 다른 것이 정상 — 같은 SKU 여러 줄이면 순차 누적되고 최종값은 마지막 줄이다.
 */
public record InboundItemResponse(
        Long id,
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        int qty,
        BigDecimal unitCost,
        int remainingQty,
        int qtyAfter,
        BigDecimal avgCostAfter
) {}
