package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.domain.OrderStatus;

import java.util.Map;

/**
 * 저장 3값 + 수량 합계 → 표시 5값·버튼 파생 규칙의 유일한 서식지 (MUL-47).
 *
 * <p>DB 는 NEW/CONFIRMED/CANCELLED 만 저장하고, 출고 진행도(shippedQty 합)를 합쳐
 * 표시 5값을 만든다. 조합 규칙은 서버 안에만 둔다는 계약(OrderStatusKey 자바독)이
 * 가리키는 곳이 여기다 — 목록·칩·상세·버튼 boolean 이 전부 이 클래스를 쓴다.
 */
public final class OrderStatusRule {

    private static final Map<OrderStatusKey, String> LABELS = Map.of(
            OrderStatusKey.NEW, "신규 주문",
            OrderStatusKey.CONFIRMED, "주문 확정",
            OrderStatusKey.PARTIALLY_SHIPPED, "부분 출고",
            OrderStatusKey.SHIPPED, "출고 완료",
            OrderStatusKey.CANCELLED, "주문 취소");

    private OrderStatusRule() {
    }

    /** 표시 키·라벨·버튼 노출을 한 번에 파생한 값. */
    public record Derived(OrderStatusKey key, String label,
                          boolean confirmable, boolean cancellable, boolean packable) {
    }

    public static Derived derive(OrderStatus status, int totalQty, int allocatedSum, int shippedSum) {
        OrderStatusKey key = key(status, totalQty, shippedSum);
        boolean isNew = key == OrderStatusKey.NEW;
        boolean packable = status == OrderStatus.CONFIRMED
                && key != OrderStatusKey.SHIPPED
                && allocatedSum < totalQty;
        return new Derived(key, label(key), isNew, isNew, packable);
    }

    public static String label(OrderStatusKey key) {
        return LABELS.get(key);
    }

    private static OrderStatusKey key(OrderStatus status, int totalQty, int shippedSum) {
        return switch (status) {
            case CANCELLED -> OrderStatusKey.CANCELLED;
            case NEW -> OrderStatusKey.NEW;
            case CONFIRMED -> (shippedSum == 0) ? OrderStatusKey.CONFIRMED
                    : (shippedSum < totalQty) ? OrderStatusKey.PARTIALLY_SHIPPED
                    : OrderStatusKey.SHIPPED;
        };
    }
}
