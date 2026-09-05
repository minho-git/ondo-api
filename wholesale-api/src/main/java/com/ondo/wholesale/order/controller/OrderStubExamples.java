package com.ondo.wholesale.order.controller;

import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.order.SettlementStatus;
import com.ondo.wholesale.order.dto.response.OrderDetailResponse;
import com.ondo.wholesale.order.dto.response.OrderItemResponse;
import com.ondo.wholesale.order.dto.response.OrderStatusResponse;
import com.ondo.wholesale.order.dto.response.PackingItemResponse;
import com.ondo.wholesale.order.dto.response.PackingQueueItemResponse;
import com.ondo.wholesale.product.domain.Size;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 계약 스텁이 반환하는 example 값 — api-lite/04_주문 문서의 Response 예시 그대로.
 * 실구현(MUL-47)이 서비스 호출로 교체한다.
 */
final class OrderStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private OrderStubExamples() {
    }

    static List<PackingQueueItemResponse> packingQueue() {
        return List.of(new PackingQueueItemResponse(
                7703L, PackingStatus.READY, null, true,
                OffsetDateTime.of(2024, 8, 1, 16, 40, 0, 0, KST),
                List.of(new PackingItemResponse(
                        91010L, 88103L, 90233L, 20, 3, "헤비웨이트 후드", "차콜", Size.XL, 20))));
    }
}
