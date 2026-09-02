package com.ondo.wholesale.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.order.SettlementStatus;

import java.time.OffsetDateTime;

/**
 * 주문 목록 한 행 (api-lite/04_주문/GET_orders.md). 주문 탭과 정산 탭이 같은 스키마를 쓴다.
 *
 * <p>화면의 "상품명 (색상) 외 N건"은 {@code summaryProductName} + {@code additionalItemCount}로
 * 프론트가 조립한다({@code 0}이면 "외 N건"을 그리지 않는다). {@code outstandingAmount}는
 * 정산 탭 미수 잔액이자 배분 입력칸의 상한.
 */
public record OrderSummaryResponse(
        Long id,
        Integer orderNumber,
        OffsetDateTime orderedAt,
        Long retailerId,
        String retailerName,
        String summaryProductName,
        int additionalItemCount,
        int orderAmount,
        OrderStatusResponse status,
        SettlementStatus settlementStatus,
        int outstandingAmount,
        @JsonProperty("isConfirmable") boolean isConfirmable,
        @JsonProperty("isCancellable") boolean isCancellable,
        @JsonProperty("isPackable") boolean isPackable
) {}
