package com.ondo.wholesale.outbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 출고 상세 (api-lite/06_출고/GET_outbounds_{outboundId}.md). 출고 확정 응답도 같은 스키마다.
 *
 * <p>{@code isShippable} = shippedAt 이 null 이고 살아있는 항목 1건 이상 — 재고 검증은 안
 * 들어 있어 true 여도 확정이 INSUFFICIENT_STOCK 으로 실패할 수 있다(버튼 활성용, 성공 보장 아님).
 * {@code packings}가 따로 있는 이유 — 합쳐진 items 만으로는 주문 역추적(CS)이 안 된다.
 */
public record OutboundDetailResponse(
        Long id,
        Integer outboundNumber,
        Long retailerId,
        String retailerCode,
        String retailerName,
        OffsetDateTime createdAt,
        OffsetDateTime shippedAt,
        Integer statementNumber,
        @JsonProperty("isShippable") boolean isShippable,
        int totalQty,
        List<OutboundItemResponse> items,
        List<PackingRef> packings
) {

    /** 이 봉투에 들어간 주문 링크. */
    public record PackingRef(Long id, Long orderId, Integer orderNumber) {}
}
