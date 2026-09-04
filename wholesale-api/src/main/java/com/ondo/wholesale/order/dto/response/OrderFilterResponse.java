package com.ondo.wholesale.order.dto.response;

import com.ondo.wholesale.order.OrderFilterKey;

/**
 * 상태 칩 하나 (api-lite/04_주문/GET_orders_filters.md). 배열 순서가 곧 칩 표시 순서.
 * 칩이 실제로 무엇을 거르는지는 서버 사정 — 프론트는 {@code key}를 목록의 {@code filter}에 넣기만 한다.
 */
public record OrderFilterResponse(OrderFilterKey key, String label, int count) {}
