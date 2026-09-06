package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.dto.request.AllocationItemRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 배분 요청 검증 (MUL-47). 확정은 전 라인 필수에 0 허용, 포장 준비는 배분할 라인만에 1 이상.
 *
 * <p>주문 수량 초과(400 {@code ALLOCATION_EXCEEDS_ORDER})와 잔량 초과(409
 * {@code ALLOCATION_EXCEEDS_REMAINING})는 다른 에러다 — 앞은 상태와 무관한 요청 오류고,
 * 뒤는 배분이 진행되며 생긴 상태와의 충돌이라 상태가 바뀌면 같은 요청이 성공할 수 있다.
 */
@Component
public class AllocationValidator {

    /** 검증에 필요한 라인 사실만 뽑은 값 — 엔티티 없이 순수 단위 테스트가 되게 한다. */
    public record OrderLine(Long id, int qty, int allocatedQty) {

        public static OrderLine of(OrderItem item) {
            return new OrderLine(item.getId(), item.getQty(), item.getAllocatedQty());
        }
    }

    /** 확정 — 전 라인 필수(누락은 400 ORDER_ITEM_MISSING), allocateQty 0 허용. */
    public List<LineAllocation> validateForConfirm(List<OrderLine> orderLines,
                                                   List<AllocationItemRequest> requested) {
        if (requested == null) {
            throw new ApiException(ErrorCode.INVARIANT_VIOLATED);
        }
        List<LineAllocation> allocations = validateCommon(orderLines, requested, 0);
        Set<Long> requestedIds = allocations.stream()
                .map(LineAllocation::orderItemId).collect(Collectors.toSet());
        if (orderLines.stream().anyMatch(line -> !requestedIds.contains(line.id()))) {
            throw new ApiException(ErrorCode.ORDER_ITEM_MISSING);
        }
        return allocations;
    }

    /** 포장 준비 — 배분할 라인만, allocateQty 1 이상, 잔량 초과는 409. */
    public List<LineAllocation> validateForPacking(List<OrderLine> orderLines,
                                                   List<AllocationItemRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(ErrorCode.INVARIANT_VIOLATED);
        }
        List<LineAllocation> allocations = validateCommon(orderLines, requested, 1);
        Map<Long, OrderLine> lines = orderLines.stream()
                .collect(Collectors.toMap(OrderLine::id, Function.identity()));
        for (LineAllocation allocation : allocations) {
            OrderLine line = lines.get(allocation.orderItemId());
            if (allocation.allocateQty() > line.qty() - line.allocatedQty()) {
                throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_REMAINING);
            }
        }
        return allocations;
    }

    private List<LineAllocation> validateCommon(List<OrderLine> orderLines,
                                                List<AllocationItemRequest> requested, int minQty) {
        Map<Long, OrderLine> lines = orderLines.stream()
                .collect(Collectors.toMap(OrderLine::id, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (AllocationItemRequest request : requested) {
            if (request.orderItemId() == null || request.allocateQty() == null
                    || request.allocateQty() < minQty) {
                throw new ApiException(ErrorCode.INVARIANT_VIOLATED);
            }
            if (!seen.add(request.orderItemId())) {
                throw new ApiException(ErrorCode.DUPLICATE_ORDER_ITEM);
            }
            OrderLine line = lines.get(request.orderItemId());
            if (line == null) {
                throw new ApiException(ErrorCode.ORDER_ITEM_NOT_IN_ORDER);
            }
            if (request.allocateQty() > line.qty()) {
                throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_ORDER);
            }
        }
        return requested.stream()
                .map(r -> new LineAllocation(r.orderItemId(), r.allocateQty()))
                .toList();
    }
}
