package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.domain.AllocationBatch;
import com.ondo.wholesale.order.domain.Backorder;
import com.ondo.wholesale.order.domain.BackorderStatus;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.repository.AllocationBatchRepository;
import com.ondo.wholesale.order.repository.BackorderRepository;
import com.ondo.wholesale.order.repository.PackingRepository;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 배분 쓰기 부품 (MUL-47). 트랜잭션 경계는 호출하는 CommandService 가 잡는다.
 *
 * <p>배분은 재고를 줄이지 않는다 — variant.reserved_qty 만 올린다(실물이 나가는 것은 출고).
 * 락 순서는 항상 주문 행(호출부) → variant 행(id 오름차순)이라 교착이 없다.
 */
@Component
@RequiredArgsConstructor
public class AllocationWriter {

    private final VariantRepository variantRepository;
    private final AllocationBatchRepository allocationBatchRepository;
    private final PackingRepository packingRepository;
    private final BackorderRepository backorderRepository;

    /**
     * 확정 배분 — 한 번에 배분·예약을 반영하고 잔량 라인마다 미송을 만든다 (D-070: 배분 1회 = batch 1행).
     *
     * @return 만들어진 포장 카드. 배분 합이 0 이면 포장 없이 null.
     */
    public Packing confirmAllocate(Long wholesalerId, Order order, List<LineAllocation> allocations) {
        Map<Long, OrderItem> items = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        Map<Long, Variant> variants = lockVariants(items, allocations);
        ensureAvailable(items, variants, allocations);

        AllocationBatch batch = allocationBatchRepository.save(
                AllocationBatch.builder().wholesalerId(wholesalerId).build());

        Packing packing = null;
        for (LineAllocation allocation : allocations) {
            if (allocation.allocateQty() == 0) {
                continue;
            }
            OrderItem item = items.get(allocation.orderItemId());
            item.allocate(allocation.allocateQty());
            variants.get(item.getVariantId()).reserve(allocation.allocateQty());
            if (packing == null) {
                packing = Packing.builder().orderId(order.getId()).build();
            }
            packing.addItem(item.getId(), null, batch.getId(), allocation.allocateQty());
        }
        if (packing != null) {
            packingRepository.save(packing);
        }

        // 배분되지 않은 잔량은 전부 미송이 된다 — 확정은 전 라인이 오므로 여기서 한 번에 만든다
        for (OrderItem item : order.getItems()) {
            int remainder = item.getQty() - item.getAllocatedQty();
            if (remainder > 0) {
                backorderRepository.save(Backorder.builder()
                        .orderItemId(item.getId()).qty(remainder).build());
            }
        }
        return packing;
    }

    /**
     * 포장 준비 배분 — OPEN 미송(FIFO 첫 건)을 찾아 연결하고, 잔량이 0 이 되면 해소한다.
     * allocateQty 는 전부 1 이상(검증 완료)이라 카드가 항상 생긴다.
     */
    public Packing packingAllocate(Long wholesalerId, Order order, List<LineAllocation> allocations) {
        Map<Long, OrderItem> items = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        Map<Long, Variant> variants = lockVariants(items, allocations);
        ensureAvailable(items, variants, allocations);

        AllocationBatch batch = allocationBatchRepository.save(
                AllocationBatch.builder().wholesalerId(wholesalerId).build());
        Packing packing = Packing.builder().orderId(order.getId()).build();
        for (LineAllocation allocation : allocations) {
            OrderItem item = items.get(allocation.orderItemId());
            Backorder backorder = backorderRepository
                    .findFirstByOrderItemIdAndStatusOrderByCreatedAtAsc(item.getId(), BackorderStatus.OPEN)
                    .orElse(null);
            item.allocate(allocation.allocateQty());
            variants.get(item.getVariantId()).reserve(allocation.allocateQty());
            packing.addItem(item.getId(), backorder == null ? null : backorder.getId(),
                    batch.getId(), allocation.allocateQty());
            if (backorder != null && item.getAllocatedQty() == item.getQty()) {
                backorder.resolve();
            }
        }
        return packingRepository.save(packing);
    }

    /** 배분에 걸린 variant 행을 id 오름차순으로 잠근다 — 재고 경합의 직렬화 지점. */
    private Map<Long, Variant> lockVariants(Map<Long, OrderItem> items, List<LineAllocation> allocations) {
        List<Long> variantIds = allocations.stream()
                .filter(a -> a.allocateQty() > 0)
                .map(a -> items.get(a.orderItemId()).getVariantId())
                .distinct().sorted(Comparator.naturalOrder()).toList();
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return variantRepository.lockAllByIdIn(variantIds).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));
    }

    /** variant 별 요청 합(같은 variant 를 쓰는 라인 합산)이 가용재고를 넘으면 409. */
    private void ensureAvailable(Map<Long, OrderItem> items, Map<Long, Variant> variants,
                                 List<LineAllocation> allocations) {
        Map<Long, Integer> requestedByVariant = allocations.stream()
                .filter(a -> a.allocateQty() > 0)
                .collect(Collectors.groupingBy(
                        a -> items.get(a.orderItemId()).getVariantId(),
                        Collectors.summingInt(LineAllocation::allocateQty)));
        for (Map.Entry<Long, Integer> entry : requestedByVariant.entrySet()) {
            Variant variant = variants.get(entry.getKey());
            if (entry.getValue() > variant.getStockQty() - variant.getReservedQty()) {
                throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }
}
