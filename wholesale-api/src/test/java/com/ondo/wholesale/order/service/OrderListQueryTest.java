package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.SettlementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 목록 쿼리 파라미터의 형식 규칙과 정렬 파싱 단위 검증 (MUL-47).
 *
 * <p>filter 는 스텁 시그니처가 이미 OrderFilterKey 타입이라 여기서 파싱하지 않는다 —
 * 미정의 값 400 은 타입 미스매치 핸들러 소관이고 HTTP 슬라이스 테스트가 잡는다.
 */
class OrderListQueryTest {

    @Test
    void 기본_정렬은_orderedAt_내림차순에_id_타이브레이커다() {
        Sort sort = of(0, 20, null).sort();
        assertThat(sort.getOrderFor("orderedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 방향을_생략하면_오름차순이다() {
        assertThat(of(0, 20, "orderNumber").sort().getOrderFor("orderNumber").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void 방향을_asc로_명시해도_오름차순이다() {
        assertThat(of(0, 20, "orderedAt,asc").sort().getOrderFor("orderedAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void 모르는_방향은_400이다() {
        검증_실패한다(() -> of(0, 20, "orderNumber,sideways"), "sort");
    }

    @Test
    void 모르는_정렬_키는_400이다() {
        검증_실패한다(() -> of(0, 20, "hack,desc"), "sort");
    }

    @Test
    void size는_100까지_되고_넘으면_400이다() {
        assertThat(of(0, 100, null).size()).isEqualTo(100);
        검증_실패한다(() -> of(0, 101, null), "size");
    }

    @Test
    void 기간은_같은_날이_되고_뒤집히면_400이다() {
        LocalDate day = LocalDate.of(2026, 9, 4);
        assertThat(OrderListQuery.of(null, null, null, null, day, day, 0, 20, null).from())
                .isEqualTo(day);
        검증_실패한다(() -> OrderListQuery.of(null, null, null, null, day.plusDays(1), day, 0, 20, null),
                "from");
    }

    @Test
    void settlementStatus는_정의된_값만_되고_미정의면_400이다() {
        assertThat(OrderListQuery.of(null, null, null, "UNPAID", null, null, 0, 20, null)
                .settlementStatus()).isEqualTo(SettlementStatus.UNPAID);
        assertThat(of(0, 20, null).settlementStatus()).isNull();
        검증_실패한다(() -> OrderListQuery.of(null, null, null, "PAID", null, null, 0, 20, null),
                "settlementStatus");
    }

    private OrderListQuery of(int page, int size, String sort) {
        return OrderListQuery.of(null, null, null, null, null, null, page, size, sort);
    }

    private void 검증_실패한다(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(ex.errors()).anySatisfy(err -> assertThat(err.field()).isEqualTo(field));
        });
    }
}
