package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.AllocationBatchResponse;
import com.ondo.wholesale.backorder.dto.AllocationPackingResponse;
import com.ondo.wholesale.backorder.dto.BackorderAllocationRequest;
import com.ondo.wholesale.backorder.dto.BackorderAllocationRequest.BackorderAllocationItem;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.domain.Backorder;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.repository.BackorderRepository;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.service.PackingAssembler;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.repository.VariantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 미송 배분(배분 확정) (MUL-48). 한 클릭 = 한 트랜잭션 = batch 1행 — 부분 성공 없음.
 *
 * <p>락 순서는 배분·출고와 같은 규약이다: 주문 행(id 오름차순) →
 * variant {@code lockAllByIdIn}(id 오름차순). 상태 검증은 전부 락 아래의 값으로 한다 —
 * 동시 배분의 패자는 락에서 풀려난 뒤의 잔량·가용으로 진다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BackorderAllocationService {

    private final JdbcClient jdbc;
    private final OrderRepository orderRepository;
    private final BackorderRepository backorderRepository;
    private final VariantRepository variantRepository;
    private final BackorderAllocationValidator validator;
    private final BackorderAllocationWriter writer;
    private final PackingAssembler packingAssembler;
    private final EntityManager em;

    public AllocationBatchResponse allocate(Long wholesalerId, BackorderAllocationRequest request) {
        List<BackorderAllocationItem> items = validator.validateShape(
                request == null ? null : request.items());

        // 미송 → 주문 매핑 (도매처 스코프) — 없는·남의 미송은 여기 안 나와 미존재(404)로 걸린다
        Map<Long, Long> orderIdByBackorderId = orderIdByBackorderId(wholesalerId, items);
        Map<Long, Order> ordersById = lockOrders(wholesalerId,
                orderIdByBackorderId.values().stream().distinct().sorted().toList());

        // 락 아래에서 미송·라인 사실을 구성한다
        Map<Long, Backorder> backorders = backorderRepository
                .findAllById(orderIdByBackorderId.keySet()).stream()
                .collect(Collectors.toMap(Backorder::getId, Function.identity()));
        Map<Long, OrderItem> orderItemsById = ordersById.values().stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        Map<Long, BackorderAllocationValidator.BackorderLine> lines = new HashMap<>();
        for (Backorder backorder : backorders.values()) {
            OrderItem orderItem = orderItemsById.get(backorder.getOrderItemId());
            lines.put(backorder.getId(), new BackorderAllocationValidator.BackorderLine(
                    backorder.getId(), backorder.getStatus(), backorder.getQty(),
                    orderItem.getQty() - orderItem.getAllocatedQty(), orderItem.getVariantId()));
        }

        // variant 행 락 — id 오름차순 일괄. 가용재고 검증의 직렬화 지점이다
        Map<Long, Variant> variantsById = lockVariants(lines);
        validator.validateAgainstState(items, lines, variantsById.values().stream()
                .collect(Collectors.toMap(Variant::getId,
                        variant -> variant.getStockQty() - variant.getReservedQty())));

        BackorderAllocationWriter.Result result = writer.allocate(wholesalerId,
                allocationOrder(items, ordersById, backorders, orderItemsById, variantsById));
        // 응답을 만드는 쪽이 variant 를 jdbc 로 읽는다 — 배분·예약 변경을 먼저 밀어넣는다
        em.flush();
        return response(result, ordersById);
    }

    private Map<Long, Long> orderIdByBackorderId(Long wholesalerId, List<BackorderAllocationItem> items) {
        record Row(long backorderId, long orderId) {
        }
        return jdbc.sql("""
                        select b.id as backorder_id, oi.order_id
                        from wholesale.backorder b
                        join wholesale.order_item oi on oi.id = b.order_item_id
                        join wholesale.orders o      on o.id = oi.order_id
                        where b.id in (:ids) and o.wholesaler_id = :wholesalerId
                        """)
                .param("ids", items.stream().map(BackorderAllocationItem::backorderId).toList())
                .param("wholesalerId", wholesalerId)
                .query((rs, rowNum) -> new Row(rs.getLong("backorder_id"), rs.getLong("order_id")))
                .list().stream()
                .collect(Collectors.toMap(Row::backorderId, Row::orderId));
    }

    /** 주문 행 락 — id 오름차순. 확정·포장·출고와 같은 직렬화 지점이다. */
    private Map<Long, Order> lockOrders(Long wholesalerId, List<Long> orderIds) {
        Map<Long, Order> orders = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            orders.put(orderId, orderRepository.lockByIdAndWholesalerId(orderId, wholesalerId)
                    .orElseThrow(() -> new ResourceNotFoundException("미송이 없거나 접근할 수 없습니다.")));
        }
        return orders;
    }

    private Map<Long, Variant> lockVariants(Map<Long, BackorderAllocationValidator.BackorderLine> lines) {
        List<Long> variantIds = lines.values().stream()
                .map(BackorderAllocationValidator.BackorderLine::variantId)
                .distinct().sorted(Comparator.naturalOrder()).toList();
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return variantRepository.lockAllByIdIn(variantIds).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));
    }

    /** 쓰기 지시 — 주문 id 오름차순(그 안은 미송 id 오름차순)으로 포장 카드 순서를 고정한다. */
    private List<BackorderAllocationWriter.BackorderAllocation> allocationOrder(
            List<BackorderAllocationItem> items, Map<Long, Order> ordersById,
            Map<Long, Backorder> backorders, Map<Long, OrderItem> orderItemsById,
            Map<Long, Variant> variantsById) {
        return items.stream()
                .map(item -> {
                    Backorder backorder = backorders.get(item.backorderId());
                    OrderItem orderItem = orderItemsById.get(backorder.getOrderItemId());
                    return new BackorderAllocationWriter.BackorderAllocation(
                            backorder, ordersById.get(orderItem.getOrder().getId()), orderItem,
                            variantsById.get(orderItem.getVariantId()), item.allocateQty());
                })
                .sorted(Comparator
                        .comparing((BackorderAllocationWriter.BackorderAllocation allocation) ->
                                allocation.order().getId())
                        .thenComparing(allocation -> allocation.backorder().getId()))
                .toList();
    }

    /** 카드 스키마는 포장 대기열과 같다 — 배분 직후 재조회 없이 그대로 그린다. */
    private AllocationBatchResponse response(BackorderAllocationWriter.Result result,
                                             Map<Long, Order> ordersById) {
        List<AllocationPackingResponse> packings = result.packings().stream()
                .map(packing -> packingCard(packing, ordersById.get(packing.getOrderId())))
                .toList();
        return new AllocationBatchResponse(result.batch().getId(), result.batch().getCreatedAt(),
                packings, result.resolvedBackorderIds());
    }

    private AllocationPackingResponse packingCard(Packing packing, Order order) {
        return new AllocationPackingResponse(
                packing.getId(), order.getId(), order.getOrderNumber(),
                packing.getStatus(), packing.getOutboundId(),
                packing.getStatus() == PackingStatus.READY && packing.getOutboundId() == null,
                packing.getCreatedAt(),
                packingAssembler.itemRows(packing.getItems(), order.getItems()));
    }
}
