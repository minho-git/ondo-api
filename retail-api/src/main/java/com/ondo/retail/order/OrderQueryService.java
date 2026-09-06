package com.ondo.retail.order;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.order.domain.OrderGroup;
import com.ondo.retail.order.dto.ActionBadge;
import com.ondo.retail.order.dto.OrderDetailResponse;
import com.ondo.retail.order.dto.OrderSummaryResponse;
import com.ondo.retail.order.dto.OrderView;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 주문 내역 · 상세 (MUL-98).
 *
 * <p>주문서는 소매 DB 에 있고 주문 내용은 도매 DB 에 있다. 둘을 여기서 합친다 —
 * 미송(MUL-97)이 주문번호를 채우던 것과 방향만 반대다.
 *
 * <p><b>도매 호출을 한 번으로 묶는다.</b> 내역 한 장에 주문서가 스무 개면 하나씩 부를 때
 * 왕복이 스무 번이다. 주문서 id 를 모아 한 번에 보내고 여기서 다시 나눈다.
 *
 * <p>상태 이름은 도매가 준 걸 그대로 쓴다. 규칙을 소매가 또 짜면 두 화면이 같은 주문을
 * 다르게 부른다.
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderGroupRepository orderGroupRepository;
    private final OrderClient orderClient;

    // ── 내역 ────────────────────────────────────────────────────

    /**
     * 주문 내역. 통합 주문서 하나가 한 줄이다.
     *
     * <p>{@code FAILED} 는 안 보인다. 도매처가 전부 거절해 주문이 하나도 안 달린
     * 껍데기라, 사용자에게는 만들어지지 않은 것으로 보여야 한다(MUL-110 · V4).
     */
    public Page<OrderSummaryResponse> orders(Long retailerId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<OrderGroup> groups = orderGroupRepository.findAccepted(
                retailerId, startOf(from), startOfNextDay(to), pageable);
        if (groups.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, List<OrderView>> byOrderGroup = fetchViews(retailerId, groups.getContent());
        return groups.map(group -> toSummary(group, byOrderGroup.getOrDefault(group.getId(), List.of())));
    }

    /**
     * 영업이 한국 기준이라 날짜도 한국 시각으로 끊는다. 새벽 영업이라 UTC 로 끊으면
     * 자정 전후 주문이 어제·오늘로 갈린다.
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 기간을 안 주면 넓은 경계를 넣는다.
     *
     * <p>쿼리에 {@code :from IS NULL} 을 쓰면 포스트그레스가 그 파라미터의 타입을 못 정해
     * 통째로 실패한다 — 값을 넣어 비교하는 자리가 하나도 없어서다. 경계를 넣어주면
     * 그 문제가 없고 쿼리도 한 갈래로 남는다.
     */
    private static final OffsetDateTime 아주_옛날 = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final OffsetDateTime 아주_먼_뒤 = OffsetDateTime.parse("9999-12-31T00:00:00Z");

    private static OffsetDateTime startOf(LocalDate date) {
        return date == null ? 아주_옛날 : date.atStartOfDay(KST).toOffsetDateTime();
    }

    /** 끝날을 포함시키려고 다음 날 0시 앞까지 잡는다. {@code <= to} 로 하면 그날이 통째로 빠진다. */
    private static OffsetDateTime startOfNextDay(LocalDate date) {
        return date == null ? 아주_먼_뒤 : date.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
    }

    private static OrderSummaryResponse toSummary(OrderGroup group, List<OrderView> views) {
        int totalQty = views.stream().flatMap(v -> v.items().stream()).mapToInt(OrderView.Item::qty).sum();
        int receivedQty = views.stream().flatMap(v -> v.items().stream()).mapToInt(OrderView.Item::receivedQty).sum();

        return new OrderSummaryResponse(
                group.getId(),
                group.getOrderNo(),
                group.getOrderedAt(),
                (int) group.getTotalAmount(),
                views.size(),
                views.stream().map(v -> v.wholesaler().name()).toList(),
                totalQty,
                receivedQty,
                totalQty - receivedQty,
                badge(views));
    }

    /**
     * 지금 할 일 뱃지.
     *
     * <p>도매처가 둘인데 하나는 확정, 하나는 출고 중이면 "이 주문의 상태" 라는 게 없다.
     * 어느 이름을 골라도 나머지에 대해 거짓이라 상태 대신 <b>할 일</b>을 준다.
     *
     * <p>{@link ActionBadge} 자바독의 순서를 그대로 따른다 — 위에서부터 먼저 걸리는 것.
     * 취소된 도매처는 빼고 본다. 일부가 취소돼도 남은 게 있으면 그쪽을 보여준다.
     */
    private static ActionBadge badge(List<OrderView> views) {
        List<String> alive = views.stream().map(OrderView::statusKey)
                .filter(key -> !"CANCELLED".equals(key)).toList();
        if (alive.isEmpty()) {
            return ActionBadge.CANCELLED;
        }
        if (alive.contains("NEW")) {
            return ActionBadge.PENDING_ACCEPT;
        }
        if (alive.contains("SHIPPED") || alive.contains("PARTIALLY_SHIPPED")) {
            return alive.stream().allMatch("SHIPPED"::equals)
                    ? ActionBadge.DONE
                    : ActionBadge.READY_TO_PICK_UP;
        }
        return ActionBadge.WAITING_SHIPMENT;
    }

    // ── 상세 ────────────────────────────────────────────────────

    /** 주문서 하나를 펼친다. 도매처별 주문이 층을 이룬다. */
    public OrderDetailResponse detail(Long retailerId, Long orderId) {
        OrderGroup group = orderGroupRepository.findById(orderId)
                .filter(g -> g.getRetailerId().equals(retailerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<OrderView> views = orderClient.findOrders(retailerId, List.of(group.getId()));

        return new OrderDetailResponse(
                group.getId(),
                group.getOrderNo(),
                group.getOrderedAt(),
                (int) group.getTotalAmount(),
                group.getAgentName(),
                group.getAgentPhone(),
                views.stream().map(OrderQueryService::toWholesalerOrder).toList());
    }

    private static OrderDetailResponse.WholesalerOrder toWholesalerOrder(OrderView view) {
        return new OrderDetailResponse.WholesalerOrder(
                view.wholesaleOrderId(),
                view.orderNumber(),
                view.wholesaler(),
                new OrderDetailResponse.Status(view.statusKey(), view.statusLabel()),
                view.paymentTerm(),
                view.receiveMethod(),
                view.amount(),
                view.cancellable(),
                view.items().stream().map(OrderQueryService::toItem).toList(),
                // 출고 기록은 도매 출고(채빈 MUL-49)가 아직 스텁이라 채울 값이 없다.
                // 빈 배열이라도 내려야 프론트가 분기를 안 만든다
                List.of());
    }

    private static OrderDetailResponse.Item toItem(OrderView.Item i) {
        return new OrderDetailResponse.Item(
                i.listingId(), i.title(), i.colorName(), i.size(),
                i.qty(), i.unitPrice(), i.qty() * i.unitPrice(),
                i.receivedQty(), i.backorderQty(), i.expectedInboundDate());
    }

    // ── 거들기 ─────────────────────────────────────────────────

    /** 주문서 여럿의 도매 주문을 한 번에 읽고 주문서별로 나눈다. */
    private Map<Long, List<OrderView>> fetchViews(Long retailerId, List<OrderGroup> groups) {
        List<OrderView> views = orderClient.findOrders(retailerId,
                groups.stream().map(OrderGroup::getId).toList());

        Map<Long, List<OrderView>> byOrderGroup = new LinkedHashMap<>();
        for (OrderView view : views) {
            byOrderGroup.computeIfAbsent(view.retailOrderId(), k -> new ArrayList<>()).add(view);
        }
        return byOrderGroup;
    }
}
