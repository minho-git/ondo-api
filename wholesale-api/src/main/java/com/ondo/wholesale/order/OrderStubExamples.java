package com.ondo.wholesale.order;

import com.ondo.wholesale.order.dto.OrderDetailResponse;
import com.ondo.wholesale.order.dto.OrderFilterResponse;
import com.ondo.wholesale.order.dto.OrderItemResponse;
import com.ondo.wholesale.order.dto.OrderStatusResponse;
import com.ondo.wholesale.order.dto.OrderSummaryResponse;
import com.ondo.wholesale.order.dto.PackingCreatedResponse;
import com.ondo.wholesale.order.dto.PackingItemResponse;
import com.ondo.wholesale.order.dto.PackingQueueItemResponse;
import com.ondo.wholesale.product.Size;

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

    static List<OrderSummaryResponse> orderSummaries() {
        return List.of(new OrderSummaryResponse(
                5531L, 1,
                OffsetDateTime.of(2024, 8, 1, 10, 22, 0, 0, KST),
                3301L, "서울유통",
                "프리미엄 오가닉 코튼 티셔츠 (블랙)", 2,
                152000,
                new OrderStatusResponse(OrderStatusKey.NEW, "신규 주문"),
                SettlementStatus.UNPAID, 0,
                true, true, false));
    }

    static List<OrderFilterResponse> orderFilters() {
        return List.of(
                new OrderFilterResponse(OrderFilterKey.ALL, "전체", 82),
                new OrderFilterResponse(OrderFilterKey.NEW, "신규 주문", 10),
                new OrderFilterResponse(OrderFilterKey.CONFIRMED, "주문 확정", 20),
                new OrderFilterResponse(OrderFilterKey.PARTIALLY_SHIPPED, "부분 출고", 15),
                new OrderFilterResponse(OrderFilterKey.SHIPPED, "출고 완료", 30),
                new OrderFilterResponse(OrderFilterKey.CANCELLED, "주문 취소", 7));
    }

    /** 확정 후 상태의 상세 — GET 상세·확정 응답 공용. */
    static OrderDetailResponse confirmedDetail() {
        return new OrderDetailResponse(
                5531L, 1,
                OffsetDateTime.of(2024, 8, 1, 10, 22, 0, 0, KST),
                OffsetDateTime.of(2024, 8, 1, 14, 5, 0, 0, KST),
                3301L, "서울유통", "010-1234-5678",
                PaymentMethod.CASH, ReceiveBy.AGENT,
                new OrderStatusResponse(OrderStatusKey.CONFIRMED, "주문 확정"),
                SettlementStatus.UNPAID,
                false, false, true,
                152000, 25,
                List.of(new OrderItemResponse(
                        88102L, 90232L, 19, 2, "린넨 플로 셔츠", "베이지", Size.L,
                        4200, 10, 6, 0, 4, 14, 4)));
    }

    /** 취소 후 상태의 상세 — NEW 주문이었으므로 배분·미송이 전부 0. */
    static OrderDetailResponse cancelledDetail() {
        return new OrderDetailResponse(
                5531L, 1,
                OffsetDateTime.of(2024, 8, 1, 10, 22, 0, 0, KST),
                null,
                3301L, "서울유통", "010-1234-5678",
                PaymentMethod.CASH, ReceiveBy.AGENT,
                new OrderStatusResponse(OrderStatusKey.CANCELLED, "주문 취소"),
                SettlementStatus.UNPAID,
                false, false, false,
                152000, 25,
                List.of(new OrderItemResponse(
                        88102L, 90232L, 19, 2, "린넨 플로 셔츠", "베이지", Size.L,
                        4200, 10, 0, 0, 10, 14, 0)));
    }

    static PackingCreatedResponse createdPacking() {
        return new PackingCreatedResponse(
                7702L, 5531L, PackingStatus.READY, null,
                OffsetDateTime.of(2024, 8, 1, 15, 2, 0, 0, KST),
                List.of(new PackingItemResponse(
                        91002L, 88102L, 90232L, 19, 2, "린넨 플로 셔츠", "베이지", Size.L, 4)));
    }

    static List<PackingQueueItemResponse> packingQueue() {
        return List.of(new PackingQueueItemResponse(
                7703L, PackingStatus.READY, null, true,
                OffsetDateTime.of(2024, 8, 1, 16, 40, 0, 0, KST),
                List.of(new PackingItemResponse(
                        91010L, 88103L, 90233L, 20, 3, "헤비웨이트 후드", "차콜", Size.XL, 20))));
    }
}
