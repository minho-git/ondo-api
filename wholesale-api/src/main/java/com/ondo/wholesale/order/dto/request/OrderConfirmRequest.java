package com.ondo.wholesale.order.dto.request;

import java.util.List;

/**
 * 주문 확정 요청 (api-lite/04_주문/POST_orders_{orderId}_confirm.md).
 *
 * <p>{@code items}는 주문의 전 라인을 빠짐없이 담는다 — 생략을 0으로 해석하면
 * 프론트 버그가 조용히 전량 미송을 만들기 때문에 400 으로 거절한다.
 */
public record OrderConfirmRequest(List<AllocationItemRequest> items) {}
