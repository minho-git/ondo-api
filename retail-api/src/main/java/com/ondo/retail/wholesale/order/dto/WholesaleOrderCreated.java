package com.ondo.retail.wholesale.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 도매가 돌려준 접수 결과 (MUL-98). */
public record WholesaleOrderCreated(
        Long id,
        Integer orderNumber,
        Long retailOrderId,
        String status,
        Integer orderAmount,
        OffsetDateTime orderedAt,
        List<Item> items) {

    public record Item(Long id, Long variantId, Integer qty, Integer unitPrice) {}
}
