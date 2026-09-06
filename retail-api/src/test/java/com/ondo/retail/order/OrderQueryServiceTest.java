package com.ondo.retail.order;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.order.domain.OrderGroup;
import com.ondo.retail.order.dto.ActionBadge;
import com.ondo.retail.order.dto.OrderDetailResponse;
import com.ondo.retail.order.dto.OrderSummaryResponse;
import com.ondo.retail.order.dto.OrderView;
import com.ondo.retail.order.dto.PaymentTerm;
import com.ondo.retail.order.dto.ReceiveMethod;
import com.ondo.retail.order.dto.WholesalerWithBank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 주문 내역 · 상세 조립 (MUL-98).
 *
 * <p>주문서는 소매 DB 에, 주문 내용은 도매 DB 에 있다. 여기서 확인할 건 <b>둘을 어떻게
 * 합치는지</b>와 <b>할 일 뱃지를 어떻게 고르는지</b>다.
 */
class OrderQueryServiceTest {

    private static final long 소매처 = 42L;

    private final OrderGroupRepository repository = mock(OrderGroupRepository.class);
    private final OrderClient orderClient = mock(OrderClient.class);
    private final OrderQueryService service = new OrderQueryService(repository, orderClient);

    // ── 내역 ────────────────────────────────────────────────────

    @Test
    @DisplayName("주문서 하나에 도매처 둘이면 장 수를 합치고 이름을 모아 준다")
    void 도매처를_합쳐_한_줄로_만든다() {
        주문서목록(주문서(5001L, "20260906-1420-0001", 87000));
        given(orderClient.findOrders(anyLong(), any())).willReturn(List.of(
                도매처주문(5001L, "무드온", "NEW", 25000, 2, 0),
                도매처주문(5001L, "라온", "NEW", 62000, 2, 0)));

        OrderSummaryResponse row = service.orders(소매처, null, null, PageRequest.of(0, 20))
                .getContent().getFirst();

        assertThat(row.wholesalerCount()).isEqualTo(2);
        assertThat(row.wholesalerNames()).containsExactly("무드온", "라온");
        assertThat(row.totalQty()).isEqualTo(4);
        assertThat(row.receivedQty()).isZero();
        assertThat(row.backorderQty()).isEqualTo(4);
        // 금액은 주문서에 저장된 값이다. 도매 응답을 다시 더하지 않는다 —
        // 접수 시점에 고정한 값이라 나중에 가격이 바뀌어도 안 흔들린다
        assertThat(row.totalAmount()).isEqualTo(87000);
    }

    @Test
    @DisplayName("주문서가 여러 개여도 도매를 한 번만 부른다")
    void 도매_호출을_묶는다() {
        주문서목록(주문서(5001L, "no-1", 1000), 주문서(5002L, "no-2", 2000), 주문서(5003L, "no-3", 3000));
        given(orderClient.findOrders(anyLong(), any())).willReturn(List.of(
                도매처주문(5001L, "무드온", "NEW", 1000, 1, 0),
                도매처주문(5002L, "라온", "NEW", 2000, 1, 0),
                도매처주문(5003L, "코튼클럽", "NEW", 3000, 1, 0)));

        service.orders(소매처, null, null, PageRequest.of(0, 20));

        // 주문서마다 부르면 내역 한 장에 왕복이 스무 번이 된다
        verify(orderClient, times(1)).findOrders(소매처, List.of(5001L, 5002L, 5003L));
    }

    @Test
    @DisplayName("주문서가 없으면 도매를 아예 안 부른다")
    void 빈_내역은_도매를_안_부른다() {
        given(repository.findAccepted(anyLong(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.orders(소매처, null, null, PageRequest.of(0, 20))).isEmpty();
        verify(orderClient, never()).findOrders(anyLong(), any());
    }

    // ── 할 일 뱃지 ──────────────────────────────────────────────

    @Test
    @DisplayName("하나라도 안 받아졌으면 PENDING_ACCEPT 다")
    void 하나라도_NEW면_접수대기다() {
        assertThat(뱃지("NEW", "CONFIRMED")).isEqualTo(ActionBadge.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("전부 확정이면 WAITING_SHIPMENT 다")
    void 전부_확정이면_출고대기다() {
        assertThat(뱃지("CONFIRMED", "CONFIRMED")).isEqualTo(ActionBadge.WAITING_SHIPMENT);
    }

    @Test
    @DisplayName("하나라도 나갔으면 찾아가라고 한다")
    void 하나라도_출고면_수령가능이다() {
        assertThat(뱃지("CONFIRMED", "PARTIALLY_SHIPPED")).isEqualTo(ActionBadge.READY_TO_PICK_UP);
        assertThat(뱃지("CONFIRMED", "SHIPPED")).isEqualTo(ActionBadge.READY_TO_PICK_UP);
    }

    @Test
    @DisplayName("전부 나갔으면 완료다")
    void 전부_출고면_완료다() {
        assertThat(뱃지("SHIPPED", "SHIPPED")).isEqualTo(ActionBadge.DONE);
    }

    @Test
    @DisplayName("취소된 도매처는 빼고 본다 — 남은 게 있으면 그쪽을 보여준다")
    void 취소는_빼고_본다() {
        assertThat(뱃지("CANCELLED", "NEW")).isEqualTo(ActionBadge.PENDING_ACCEPT);
        // 전부 취소여야 취소다
        assertThat(뱃지("CANCELLED", "CANCELLED")).isEqualTo(ActionBadge.CANCELLED);
    }

    // ── 상세 ────────────────────────────────────────────────────

    @Test
    @DisplayName("상세는 도매처별 주문을 층으로 펼친다")
    void 상세를_펼친다() {
        given(repository.findById(5001L)).willReturn(Optional.of(주문서(5001L, "20260906-1420-0001", 87000)));
        given(orderClient.findOrders(anyLong(), any())).willReturn(List.of(
                도매처주문(5001L, "무드온", "NEW", 25000, 2, 0)));

        OrderDetailResponse detail = service.detail(소매처, 5001L);

        assertThat(detail.orderNo()).isEqualTo("20260906-1420-0001");
        assertThat(detail.agentName()).isEqualTo("박삼촌");
        assertThat(detail.wholesalerOrders()).hasSize(1);

        OrderDetailResponse.WholesalerOrder w = detail.wholesalerOrders().getFirst();
        // 상태 이름을 소매가 다시 만들지 않는다. 도매가 준 걸 그대로 쓴다
        assertThat(w.status().key()).isEqualTo("NEW");
        assertThat(w.status().label()).isEqualTo("신규 주문");
        assertThat(w.isCancellable()).isTrue();
        assertThat(w.items().getFirst().lineAmount()).isEqualTo(25000);
        // 출고는 도매 MUL-49 가 아직 스텁이라 빈 배열이다. null 이면 프론트가 분기를 만든다
        assertThat(w.outbounds()).isEmpty();
    }

    @Test
    @DisplayName("남의 주문서는 못 연다")
    void 남의_주문서는_막는다() {
        given(repository.findById(5001L)).willReturn(Optional.of(주문서(5001L, "no", 1000)));

        assertThatThrownBy(() -> service.detail(999L, 5001L))
                .isInstanceOf(BusinessException.class);

        verify(orderClient, never()).findOrders(anyLong(), any());
    }

    // ── 가짜 ────────────────────────────────────────────────────

    private ActionBadge 뱃지(String... statusKeys) {
        주문서목록(주문서(5001L, "no", 1000));
        given(orderClient.findOrders(anyLong(), any())).willReturn(
                java.util.Arrays.stream(statusKeys)
                        .map(key -> 도매처주문(5001L, "무드온", key, 1000, 1, 0))
                        .toList());

        return service.orders(소매처, null, null, PageRequest.of(0, 20))
                .getContent().getFirst().actionBadge();
    }

    private void 주문서목록(OrderGroup... groups) {
        given(repository.findAccepted(anyLong(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(groups), PageRequest.of(0, 20), groups.length));
    }

    private static OrderGroup 주문서(long id, String orderNo, long totalAmount) {
        OrderGroup group = OrderGroup.builder()
                .retailerId(소매처).requestId("key-" + id).orderNo(orderNo)
                .agentName("박삼촌").agentPhone("01033330001")
                .orderedAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(group, "id", id);
        ReflectionTestUtils.setField(group, "totalAmount", totalAmount);
        return group;
    }

    private static OrderView 도매처주문(long retailOrderId, String name, String statusKey,
                                    int amount, int qty, int received) {
        return new OrderView(
                retailOrderId, 8800L, 1,
                new WholesalerWithBank(101L, name, "청평화", "2층", null, null, null),
                statusKey, label(statusKey),
                PaymentTerm.CASH, ReceiveMethod.AGENT,
                amount, "NEW".equals(statusKey),
                List.of(new OrderView.Item(2001L, "빈티지 셔츠", "레드", "S",
                        qty, amount / qty, received, qty - received, null)));
    }

    private static String label(String key) {
        return switch (key) {
            case "NEW" -> "신규 주문";
            case "CONFIRMED" -> "주문 확정";
            case "PARTIALLY_SHIPPED" -> "부분 출고";
            case "SHIPPED" -> "출고 완료";
            default -> "주문 취소";
        };
    }
}
