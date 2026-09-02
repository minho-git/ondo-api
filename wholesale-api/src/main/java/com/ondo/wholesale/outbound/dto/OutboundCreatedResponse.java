package com.ondo.wholesale.outbound.dto;

import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.dto.PackingItemResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 포장 완료 201 응답. {@code shippedAt}·{@code statementNumber}는 아직 {@code null} —
 * 출고 확정 때 채워진다. 포장이 분할되면 {@code packings[].id}가 요청에 없던 새 값일 수 있다
 * (대기열에 남는 쪽의 id 가 유지되고 나가는 쪽이 새로 생긴다).
 */
public record OutboundCreatedResponse(
        Long id,
        Integer outboundNumber,
        Long retailerId,
        String retailerName,
        OffsetDateTime shippedAt,
        Integer statementNumber,
        OffsetDateTime createdAt,
        int totalQty,
        List<Packing> packings
) {

    /** 봉투에 담긴 포장(주문 단위) 하나. 묶인 뒤라 status 는 PACKED. */
    public record Packing(Long id, Long orderId, Integer orderNumber, PackingStatus status,
                          List<PackingItemResponse> items) {}
}
