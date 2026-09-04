package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.order.dto.response.OrderDetailResponse;
import com.ondo.wholesale.order.dto.response.OrderFilterResponse;
import com.ondo.wholesale.order.dto.response.OrderStatusResponse;
import com.ondo.wholesale.order.dto.response.OrderSummaryResponse;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 목록·상태 칩·단건 조회 (MUL-47).
 *
 * <p>목록은 페이지 쿼리 1방 + 배치 4방(거래처, 첫 라인, 수량 합계, 정산)으로 조립한다 —
 * 주문마다 되묻는 N+1 이 없다. 정산 상태 필터만 원장 스캔이라 id 를 먼저 골라 idIn 으로 합류한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    /** 칩 표시 순서 — 배열 순서가 곧 화면 순서라는 계약. */
    private static final List<OrderFilterKey> CHIP_ORDER = List.of(
            OrderFilterKey.ALL, OrderFilterKey.NEW, OrderFilterKey.CONFIRMED,
            OrderFilterKey.PARTIALLY_SHIPPED, OrderFilterKey.SHIPPED, OrderFilterKey.CANCELLED);

    private final OrderRepository orderRepository;
    private final PartnerRepository partnerRepository;
    private final OrderSummaryReader reader;
    private final OrderDetailAssembler orderDetailAssembler;

    public ApiResponse<List<OrderSummaryResponse>> list(Long wholesalerId, OrderListQuery query) {
        // Specification.allOf 는 null 요소를 거부한다 — 조건이 있을 때만 담는다
        List<Specification<Order>> conditions = new ArrayList<>(List.of(OrderSpecs.ownedBy(wholesalerId)));
        if (query.q() != null && !query.q().isBlank()) {
            conditions.add(OrderSpecs.searchLike(query.q()));
        }
        if (query.filter() != null) {
            conditions.add(OrderSpecs.derivedStatus(query.filter()));
        }
        if (query.retailerId() != null) {
            conditions.add(OrderSpecs.retailerScoped(query.retailerId()));
        }
        if (query.from() != null || query.to() != null) {
            conditions.add(OrderSpecs.orderedBetween(query.from(), query.to()));
        }
        if (query.settlementStatus() != null) {
            conditions.add(OrderSpecs.idIn(
                    reader.orderIdsBySettlement(wholesalerId, query.settlementStatus())));
        }

        Page<Order> orders = orderRepository.findAll(Specification.allOf(conditions),
                PageRequest.of(query.page(), query.size(), query.sort()));

        List<Long> ids = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, OrderSummaryReader.FirstLine> firstLines = reader.firstLines(ids);
        Map<Long, OrderSummaryReader.QtySums> sums = reader.qtySums(ids);
        Map<Long, OrderSummaryReader.Settlement> settlements = reader.settlements(ids);
        Map<Long, Partner> partners = partners(orders.getContent());

        List<OrderSummaryResponse> rows = orders.getContent().stream()
                .map(order -> summaryRow(order, firstLines.get(order.getId()),
                        sums.get(order.getId()), settlements.get(order.getId()),
                        partners.get(order.getPartnerId())))
                .toList();

        return ApiResponse.paged(rows, new ApiResponse.PageMeta(
                query.page(), query.size(), orders.getTotalElements(), orders.getTotalPages()));
    }

    public List<OrderFilterResponse> filters(Long wholesalerId, String q, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validationFailed("from", "from 이 to 보다 뒤일 수 없다.");
        }
        Map<OrderFilterKey, Long> counts = reader.chipCounts(wholesalerId, q, from, to);
        return CHIP_ORDER.stream()
                .map(key -> new OrderFilterResponse(key, chipLabel(key), counts.get(key).intValue()))
                .toList();
    }

    public OrderDetailResponse detail(Long wholesalerId, Long orderId) {
        Order order = orderRepository.findByIdAndWholesalerId(orderId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다."));
        return orderDetailAssembler.assemble(order);
    }

    private Map<Long, Partner> partners(List<Order> orders) {
        List<Long> partnerIds = orders.stream().map(Order::getPartnerId).distinct().toList();
        return partnerRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(Partner::getId, Function.identity()));
    }

    private OrderSummaryResponse summaryRow(Order order, OrderSummaryReader.FirstLine firstLine,
                                            OrderSummaryReader.QtySums sums,
                                            OrderSummaryReader.Settlement settlement, Partner partner) {
        OrderStatusRule.Derived derived = OrderStatusRule.derive(order.getStatus(),
                sums.totalQty(), sums.allocatedSum(), sums.shippedSum());
        return new OrderSummaryResponse(
                order.getId(), order.getOrderNumber(), order.getOrderedAt(),
                partner.getRetailerId(), partner.getRetailerName(),
                firstLine.productName() + " (" + firstLine.colorName() + ")",
                firstLine.additionalCount(), sums.orderAmount(),
                new OrderStatusResponse(derived.key(), derived.label()),
                settlement.status(), settlement.outstandingAmount(),
                derived.confirmable(), derived.cancellable(), derived.packable());
    }

    private String chipLabel(OrderFilterKey key) {
        return (key == OrderFilterKey.ALL) ? "전체"
                : OrderStatusRule.label(OrderStatusKey.valueOf(key.name()));
    }
}
