package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderStatus;
import com.ondo.wholesale.order.dto.request.OrderConfirmRequest;
import com.ondo.wholesale.order.dto.response.OrderDetailResponse;
import com.ondo.wholesale.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 확정·취소 (MUL-47). 한 트랜잭션 = 이 클래스의 메서드 하나.
 *
 * <p>주문 행을 비관 락으로 잡아 주문 안의 경쟁(확정·취소·포장)을 직렬화한다.
 * variant 락은 {@link AllocationWriter}가 항상 그 다음 순서로 잡는다 — 교착 없음.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final AllocationValidator allocationValidator;
    private final AllocationWriter allocationWriter;
    private final OrderDetailAssembler orderDetailAssembler;
    private final EntityManager em;

    /** 확정 — 한 트랜잭션에 전이·배분·미송 생성. 응답은 상세와 동일한 스키마다(재조회 없이 갱신). */
    public OrderDetailResponse confirm(Long wholesalerId, Long orderId, OrderConfirmRequest request) {
        Order order = lockOrder(wholesalerId, orderId);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        List<AllocationValidator.OrderLine> lines = order.getItems().stream()
                .map(AllocationValidator.OrderLine::of).toList();
        List<LineAllocation> allocations = allocationValidator.validateForConfirm(
                lines, request == null ? null : request.items());
        allocationWriter.confirmAllocate(wholesalerId, order, allocations);
        order.confirm();
        return assemble(order);
    }

    /** 취소 — NEW 전용. 확정된 주문은 이미 배분·미송이 달려 있어 409. */
    public OrderDetailResponse cancel(Long wholesalerId, Long orderId) {
        Order order = lockOrder(wholesalerId, orderId);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        order.cancel();
        return assemble(order);
    }

    private Order lockOrder(Long wholesalerId, Long orderId) {
        return orderRepository.lockByIdAndWholesalerId(orderId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다."));
    }

    private OrderDetailResponse assemble(Order order) {
        // 조립기가 variant 가용재고를 jdbc 로 읽는다 — 예약 변경을 먼저 밀어넣는다 (jdbc 는 flush 를 유발하지 않음)
        em.flush();
        return orderDetailAssembler.assemble(order);
    }
}
