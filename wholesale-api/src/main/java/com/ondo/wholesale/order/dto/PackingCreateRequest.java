package com.ondo.wholesale.order.dto;

import java.util.List;

/**
 * 포장 준비(추가 배분) 요청 (api-lite/04_주문/POST_orders_{orderId}_packings.md).
 *
 * <p>확정과 달리 배분할 라인만 담는다. 미송 id 는 담지 않는다 — 한 라인의 OPEN 미송은
 * 최대 1건이라 서버가 {@code orderItemId}로 찾아 연결한다.
 */
public record PackingCreateRequest(List<AllocationItemRequest> items) {}
