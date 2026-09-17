package com.ondo.wholesale.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.order.SettlementStatus;

import java.time.OffsetDateTime;

/**
 * 주문 목록 한 행 (api-lite/04_주문/GET_orders.md). 주문 탭과 정산 탭이 같은 스키마를 쓴다.
 *
 * <p>화면의 "상품명 (색상) 외 N건"은 {@code summaryProductName} + {@code additionalItemCount}로
 * 프론트가 조립한다({@code 0}이면 "외 N건"을 그리지 않는다). {@code shippedAmount}는 출고로 생긴 미수
 * (출고 전 0), {@code outstandingAmount}는 거기서 붙은 돈을 뺀 값 — 정산 탭 미수 잔액이자 배분 입력칸의 상한.
 * 정산 상태도 출고분 기준이다: 붙은 돈 0 이면 UNPAID, 남은 미수가 있으면 PARTIALLY_SETTLED, 없으면 SETTLED (MUL-126).
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
        int shippedAmount,
        OrderStatusResponse status,
        SettlementStatus settlementStatus,
        int outstandingAmount,
        @JsonProperty("isConfirmable") boolean isConfirmable,
        @JsonProperty("isCancellable") boolean isCancellable,
        @JsonProperty("isPackable") boolean isPackable
) {}
