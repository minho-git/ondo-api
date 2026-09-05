package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.dto.request.AllocationItemRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배분 요청 검증 단위 테스트 (MUL-47).
 *
 * <p>확정은 전 라인 필수에 0 허용, 포장 준비는 배분할 라인만에 1 이상.
 * 주문 수량 초과(400)와 잔량 초과(409)는 다른 에러다 — 앞은 상태와 무관한
 * 요청 오류고, 뒤는 배분이 진행되며 생긴 상태와의 충돌이다.
 */
class AllocationValidatorTest {

    private final AllocationValidator validator = new AllocationValidator();

    // 주문 라인: (id=11, qty=5, allocated=0), (id=12, qty=4, allocated=0)
    private final List<AllocationValidator.OrderLine> 라인들 = List.of(
            new AllocationValidator.OrderLine(11L, 5, 0),
            new AllocationValidator.OrderLine(12L, 4, 0));

    @Test
    void 확정은_전량_0이어도_전_라인이_있으면_통과한다() {
        List<LineAllocation> result = validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, 0), new AllocationItemRequest(12L, 0)));

        assertThat(result).containsExactly(new LineAllocation(11L, 0), new LineAllocation(12L, 0));
    }

    @Test
    void 확정에서_라인이_빠지면_ORDER_ITEM_MISSING이다() {
        실패한다(() -> validator.validateForConfirm(라인들,
                List.of(new AllocationItemRequest(11L, 2))), ErrorCode.ORDER_ITEM_MISSING);
    }

    @Test
    void 확정에서_빈_목록도_ORDER_ITEM_MISSING이다() {
        실패한다(() -> validator.validateForConfirm(라인들, List.of()), ErrorCode.ORDER_ITEM_MISSING);
    }

    @Test
    void items가_null이면_INVARIANT_VIOLATED다() {
        실패한다(() -> validator.validateForConfirm(라인들, null), ErrorCode.INVARIANT_VIOLATED);
    }

    @Test
    void allocateQty가_null이거나_음수면_INVARIANT_VIOLATED다() {
        실패한다(() -> validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, null), new AllocationItemRequest(12L, 0))),
                ErrorCode.INVARIANT_VIOLATED);
        실패한다(() -> validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, -1), new AllocationItemRequest(12L, 0))),
                ErrorCode.INVARIANT_VIOLATED);
    }

    @Test
    void 같은_라인을_두_번_담으면_DUPLICATE_ORDER_ITEM이다() {
        실패한다(() -> validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, 1), new AllocationItemRequest(11L, 1),
                new AllocationItemRequest(12L, 0))), ErrorCode.DUPLICATE_ORDER_ITEM);
    }

    @Test
    void 주문에_없는_라인은_ORDER_ITEM_NOT_IN_ORDER다() {
        실패한다(() -> validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, 1), new AllocationItemRequest(99L, 1))),
                ErrorCode.ORDER_ITEM_NOT_IN_ORDER);
    }

    @Test
    void 주문_수량을_넘으면_ALLOCATION_EXCEEDS_ORDER다() {
        실패한다(() -> validator.validateForConfirm(라인들, List.of(
                new AllocationItemRequest(11L, 6), new AllocationItemRequest(12L, 0))),
                ErrorCode.ALLOCATION_EXCEEDS_ORDER);
    }

    @Test
    void 포장준비는_배분할_라인만_담아도_된다() {
        List<AllocationValidator.OrderLine> 진행중 = List.of(
                new AllocationValidator.OrderLine(11L, 5, 2),
                new AllocationValidator.OrderLine(12L, 4, 0));

        List<LineAllocation> result = validator.validateForPacking(진행중,
                List.of(new AllocationItemRequest(11L, 3)));

        assertThat(result).containsExactly(new LineAllocation(11L, 3));
    }

    @Test
    void 포장준비에서_null이나_빈_목록은_INVARIANT_VIOLATED다() {
        실패한다(() -> validator.validateForPacking(라인들, null), ErrorCode.INVARIANT_VIOLATED);
        실패한다(() -> validator.validateForPacking(라인들, List.of()), ErrorCode.INVARIANT_VIOLATED);
    }

    @Test
    void 포장준비에서_0은_INVARIANT_VIOLATED다() {
        실패한다(() -> validator.validateForPacking(라인들,
                List.of(new AllocationItemRequest(11L, 0))), ErrorCode.INVARIANT_VIOLATED);
    }

    @Test
    void 포장준비에서_잔량을_넘으면_ALLOCATION_EXCEEDS_REMAINING이다() {
        List<AllocationValidator.OrderLine> 진행중 = List.of(
                new AllocationValidator.OrderLine(11L, 5, 2));

        실패한다(() -> validator.validateForPacking(진행중,
                List.of(new AllocationItemRequest(11L, 4))), ErrorCode.ALLOCATION_EXCEEDS_REMAINING);
    }

    @Test
    void 포장준비에서_주문_수량_자체를_넘으면_잔량이_아니라_EXCEEDS_ORDER다() {
        List<AllocationValidator.OrderLine> 진행중 = List.of(
                new AllocationValidator.OrderLine(11L, 5, 2));

        실패한다(() -> validator.validateForPacking(진행중,
                List.of(new AllocationItemRequest(11L, 6))), ErrorCode.ALLOCATION_EXCEEDS_ORDER);
    }

    private void 실패한다(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(expected));
    }
}
