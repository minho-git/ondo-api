package com.ondo.wholesale.order.dto;

/**
 * 라인 하나의 배분 지시. 확정에선 {@code allocateQty: 0}이 정상값(전량 미송),
 * 포장 준비에선 {@code >= 1}만 허용(0은 아무 일도 안 하는 요청이라 거절).
 */
public record AllocationItemRequest(Long orderItemId, Integer allocateQty) {}
