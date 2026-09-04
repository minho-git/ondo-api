package com.ondo.wholesale.order.dto.response;

import com.ondo.wholesale.order.OrderStatusKey;

/**
 * 화면의 "주문 상태" 칸. 프론트는 {@code label}을 그대로 그리고, 버튼 노출은
 * 이 값이 아니라 {@code isConfirmable} 류 boolean 으로 판단한다.
 */
public record OrderStatusResponse(OrderStatusKey key, String label) {}
