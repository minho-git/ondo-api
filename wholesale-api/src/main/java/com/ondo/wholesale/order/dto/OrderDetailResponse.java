package com.ondo.wholesale.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.order.SettlementStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 상세 (api-lite/04_주문/GET_orders_{orderId}.md). 확정·취소 응답도 같은 스키마다.
 *
 * <p>미송 상세·포장 대기 카드는 이 응답에 없다 — 라인의 미송 잔여 합은 {@code items[].backorderQty},
 * 카드는 포장 대기열을 따로 부른다.
 */
public record OrderDetailResponse(
        Long id,
        Integer orderNumber,
        OffsetDateTime orderedAt,
        OffsetDateTime confirmedAt,
        Long retailerId,
        String retailerName,
        String retailerPhone,
        PaymentMethod expectedPaymentMethod,
        ReceiveBy receiveBy,
        OrderStatusResponse status,
        SettlementStatus settlementStatus,
        @JsonProperty("isConfirmable") boolean isConfirmable,
        @JsonProperty("isCancellable") boolean isCancellable,
        @JsonProperty("isPackable") boolean isPackable,
        int orderAmount,
        int totalQty,
        List<OrderItemResponse> items
) {}
