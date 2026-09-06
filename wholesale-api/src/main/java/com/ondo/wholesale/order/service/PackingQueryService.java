package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.dto.response.PackingQueueItemResponse;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.repository.PackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 포장 대기열 조회 (MUL-47). 페이징 없음.
 *
 * <p>취소된 포장(전 항목 배분취소)은 나오지 않는다. 삭제 버튼 노출은 isCancellable
 * 하나로 판단한다는 계약이라, READY 이면서 출고에 안 잡힌 것만 참이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackingQueryService {

    private final OrderRepository orderRepository;
    private final PackingRepository packingRepository;
    private final PackingAssembler packingAssembler;

    public List<PackingQueueItemResponse> queue(Long wholesalerId, Long orderId,
                                                String status, String sort) {
        Order order = orderRepository.findByIdAndWholesalerId(orderId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다."));
        PackingStatus statusFilter = parseStatus(status);
        boolean latestFirst = parseSort(sort);

        return packingRepository.findByOrderId(orderId).stream()
                .filter(packing -> packing.getItems().stream().anyMatch(i -> i.getDeletedAt() == null))
                .filter(packing -> statusFilter == null || packing.getStatus() == statusFilter)
                .sorted(latestFirst
                        ? Comparator.comparing(Packing::getCreatedAt).thenComparing(Packing::getId).reversed()
                        : Comparator.comparing(Packing::getCreatedAt).thenComparing(Packing::getId))
                .map(packing -> new PackingQueueItemResponse(
                        packing.getId(), packing.getStatus(), packing.getOutboundId(),
                        packing.getStatus() == PackingStatus.READY && packing.getOutboundId() == null,
                        packing.getCreatedAt(),
                        packingAssembler.itemRows(packing.getItems(), order.getItems())))
                .toList();
    }

    private PackingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PackingStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.validationFailed("status", "정의되지 않은 포장 상태: " + raw);
        }
    }

    /** 기본은 만든 순서(createdAt 오름차순). "createdAt,desc"만 추가로 허용한다. */
    private boolean parseSort(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("createdAt") || raw.equals("createdAt,asc")) {
            return false;
        }
        if (raw.equals("createdAt,desc")) {
            return true;
        }
        throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
    }
}
