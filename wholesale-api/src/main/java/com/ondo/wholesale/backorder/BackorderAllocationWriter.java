package com.ondo.wholesale.backorder;

import com.ondo.wholesale.order.domain.AllocationBatch;
import com.ondo.wholesale.order.domain.Backorder;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.repository.AllocationBatchRepository;
import com.ondo.wholesale.order.repository.PackingRepository;
import com.ondo.wholesale.product.domain.Variant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 미송 배분 쓰기 부품 (MUL-48). 트랜잭션 경계와 락(주문 행 오름차순 → variant
 * {@code lockAllByIdIn})·검증은 호출하는 서비스가 끝낸 뒤다.
 *
 * <p>기존 {@code AllocationWriter}를 재사용하지 않는 이유 — 그쪽은 호출마다 batch 를
 * 만들어 "클릭 1회 = batch 1개, 주문마다 포장 1장"인 이 계약과 맞지 않는다.
 * 배분은 재고를 줄이지 않는다 — variant.reserved_qty 만 올린다(실물이 나가는 것은 출고).
 */
@Component
@RequiredArgsConstructor
public class BackorderAllocationWriter {

    private final AllocationBatchRepository allocationBatchRepository;
    private final PackingRepository packingRepository;

    /** 검증을 통과한 배분 지시 하나 — 전부 락 아래에서 읽은 엔티티다. */
    public record BackorderAllocation(Backorder backorder, Order order, OrderItem orderItem,
                                      Variant variant, int allocateQty) {
    }

    /** 클릭 1회의 결과. packings 는 주문 id 오름차순, resolved 는 잔량이 0 이 된 미송만이다. */
    public record Result(AllocationBatch batch, List<Packing> packings,
                         List<Long> resolvedBackorderIds) {
    }

    /** 배분 1회 = batch 1행 (D-070) — 주문마다 포장 카드가 한 장씩 생긴다. */
    public Result allocate(Long wholesalerId, List<BackorderAllocation> allocations) {
        AllocationBatch batch = allocationBatchRepository.save(
                AllocationBatch.builder().wholesalerId(wholesalerId).build());

        Map<Long, Packing> packingByOrder = new LinkedHashMap<>(); // 입력이 주문 id 오름차순
        List<Long> resolvedBackorderIds = new ArrayList<>();
        for (BackorderAllocation allocation : allocations) {
            Packing packing = packingByOrder.computeIfAbsent(allocation.order().getId(),
                    orderId -> Packing.builder().orderId(orderId).build());
            allocation.orderItem().allocate(allocation.allocateQty());
            allocation.variant().reserve(allocation.allocateQty());
            packing.addItem(allocation.orderItem().getId(), allocation.backorder().getId(),
                    batch.getId(), allocation.allocateQty());
            // 잔량(qty − allocated)이 0 이 된 미송만 해소한다 — 부분 배분은 OPEN 으로 남는다
            if (allocation.orderItem().getAllocatedQty() == allocation.orderItem().getQty()) {
                allocation.backorder().resolve();
                resolvedBackorderIds.add(allocation.backorder().getId());
            }
        }
        packingByOrder.values().forEach(packingRepository::save);
        return new Result(batch, List.copyOf(packingByOrder.values()), resolvedBackorderIds);
    }
}
