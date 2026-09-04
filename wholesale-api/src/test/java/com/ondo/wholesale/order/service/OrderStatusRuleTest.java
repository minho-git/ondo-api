package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장 3값 + 수량 합계 → 표시 5값·버튼 파생 규칙 단위 검증 (MUL-47).
 *
 * <p>조합 규칙은 서버 안에만 둔다는 계약(OrderStatusKey 자바독)의 그 "서버 안"이 이 클래스다.
 * 목록·칩·상세·버튼 boolean 이 전부 여기 의존한다.
 */
class OrderStatusRuleTest {

    @Test
    void 취소된_주문은_출고와_무관하게_CANCELLED다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.CANCELLED, 10, 4, 2);
        assertThat(d.key()).isEqualTo(OrderStatusKey.CANCELLED);
        assertThat(d.confirmable()).isFalse();
        assertThat(d.cancellable()).isFalse();
        assertThat(d.packable()).isFalse();
    }

    @Test
    void 신규_주문은_확정과_취소만_할_수_있다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.NEW, 10, 0, 0);
        assertThat(d.key()).isEqualTo(OrderStatusKey.NEW);
        assertThat(d.confirmable()).isTrue();
        assertThat(d.cancellable()).isTrue();
        assertThat(d.packable()).isFalse();
    }

    @Test
    void 확정_후_미출고면_CONFIRMED고_배분_잔량이_있으면_포장할_수_있다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.CONFIRMED, 10, 4, 0);
        assertThat(d.key()).isEqualTo(OrderStatusKey.CONFIRMED);
        assertThat(d.confirmable()).isFalse();
        assertThat(d.cancellable()).isFalse();
        assertThat(d.packable()).isTrue();
    }

    @Test
    void 전량_배분된_확정_주문은_포장할_수_없다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.CONFIRMED, 10, 10, 0);
        assertThat(d.key()).isEqualTo(OrderStatusKey.CONFIRMED);
        assertThat(d.packable()).isFalse();
    }

    @Test
    void 일부_출고면_PARTIALLY_SHIPPED다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.CONFIRMED, 10, 10, 3);
        assertThat(d.key()).isEqualTo(OrderStatusKey.PARTIALLY_SHIPPED);
    }

    @Test
    void 전량_출고면_SHIPPED고_아무_버튼도_안_뜬다() {
        OrderStatusRule.Derived d = OrderStatusRule.derive(OrderStatus.CONFIRMED, 10, 10, 10);
        assertThat(d.key()).isEqualTo(OrderStatusKey.SHIPPED);
        assertThat(d.confirmable()).isFalse();
        assertThat(d.cancellable()).isFalse();
        assertThat(d.packable()).isFalse();
    }

    @Test
    void 키마다_한글_라벨이_있다() {
        assertThat(OrderStatusRule.label(OrderStatusKey.NEW)).isEqualTo("신규 주문");
        assertThat(OrderStatusRule.label(OrderStatusKey.CONFIRMED)).isEqualTo("주문 확정");
        assertThat(OrderStatusRule.label(OrderStatusKey.PARTIALLY_SHIPPED)).isEqualTo("부분 출고");
        assertThat(OrderStatusRule.label(OrderStatusKey.SHIPPED)).isEqualTo("출고 완료");
        assertThat(OrderStatusRule.label(OrderStatusKey.CANCELLED)).isEqualTo("주문 취소");
    }
}
