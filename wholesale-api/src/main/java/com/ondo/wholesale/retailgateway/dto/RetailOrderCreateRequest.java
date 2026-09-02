package com.ondo.wholesale.retailgateway.dto;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;

import java.util.List;

/**
 * 소매 백엔드의 주문 생성 요청 (api/08_소매접점.md §2.2).
 *
 * <p>{@code retailOrderId}·{@code retailerId}는 소매 DB 의 외부 식별자다(FK 아님, D-051·D-052).
 * {@code wholesalerId}는 소매가 보내고 도매가 대조한다 — 유도하지 않는 이유는 소매 버그를
 * 조용히 삼키지 않기 위함. 중복 주문은 UNIQUE(retailOrderId, wholesalerId)가 막는다.
 */
public record RetailOrderCreateRequest(
        Long retailOrderId,
        Long retailerId,
        Long wholesalerId,
        PaymentMethod expectedPaymentMethod,
        ReceiveBy receiveBy,
        List<RetailOrderItemRequest> items
) {}
