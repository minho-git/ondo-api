package com.ondo.wholesale.outbound.dto;

import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.product.domain.Size;

import java.time.OffsetDateTime;

/**
 * 포장 대기 펼침의 SKU 행 하나. {@code id}가 포장 완료 요청의 {@code packingItemIds}다.
 * {@code packingId}를 담는 이유 — 같은 포장의 항목을 전부 체크했는지 보여줘야
 * "이 주문은 통째로 나간다"를 알 수 있다(일부만 체크하면 포장이 쪼개진다).
 */
public record PackingItemRowResponse(
        Long id,
        Long packingId,
        Long orderId,
        Integer orderNumber,
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        String productName,
        String color,
        Size size,
        ReceiveBy receiveBy,
        OffsetDateTime orderedAt,
        int qty
) {}
