package com.ondo.retail.order.dto;

import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 접수 결과.
 *
 * <p><b>일부만 접수돼도 201 이다. 에러가 아니라 결과다.</b> 도매처 넷 중 셋만 받아졌으면
 * 그건 정상적인 결과이지 실패가 아니다. 접수된 항목만 장바구니에서 빠진다.
 *
 * <p>전부 안 되면 통합 주문을 만들지 않는다 — 그때는 502 다.
 */
public record PlaceOrderResponse(
        Long orderId,
        String orderNo,
        OffsetDateTime orderedAt,
        int totalAmount,
        List<Result> results) {

    /**
     * @param wholesaleOrderId 도매쪽 주문 id. 실패면 null
     * @param orderNumber      도매처별 연번. 실패면 null
     * @param reason           실패 코드. 성공이면 null
     * @param message          화면에 그대로 쓸 문구
     */
    @Schema(name = "PlaceOrderResult")
    public record Result(
            Long wholesalerId,
            String wholesalerName,
            boolean isAccepted,
            Long wholesaleOrderId,
            Integer orderNumber,
            Integer amount,
            String reason,
            String message) {
    }
}
