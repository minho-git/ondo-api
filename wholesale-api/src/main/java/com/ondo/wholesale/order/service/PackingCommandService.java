package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderStatus;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.dto.request.PackingCreateRequest;
import com.ondo.wholesale.order.dto.response.PackingCreatedResponse;
import com.ondo.wholesale.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포장 준비(추가 배분) (MUL-47). 한 요청 = 포장 대기 카드 1장.
 *
 * <p>확정된 주문에서만 된다 — 신규는 확정을, 취소는 되돌릴 수 없음을 뜻하므로 409.
 * 락 순서는 확정과 동일하게 주문 행 → variant 행이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PackingCommandService {

    private final OrderRepository orderRepository;
    private final AllocationValidator allocationValidator;
    private final AllocationWriter allocationWriter;
    private final PackingAssembler packingAssembler;
    private final EntityManager em;

    public PackingCreatedResponse create(Long wholesalerId, Long orderId, PackingCreateRequest request) {
        Order order = orderRepository.lockByIdAndWholesalerId(orderId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다."));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        List<AllocationValidator.OrderLine> lines = order.getItems().stream()
                .map(AllocationValidator.OrderLine::of).toList();
        List<LineAllocation> allocations = allocationValidator.validateForPacking(
                lines, request == null ? null : request.items());
        Packing packing = allocationWriter.packingAllocate(wholesalerId, order, allocations);
        // 조립기가 variant 를 jdbc 로 읽는다 — 예약 변경을 먼저 밀어넣는다
        em.flush();
        return packingAssembler.created(packing, order.getItems());
    }
}
