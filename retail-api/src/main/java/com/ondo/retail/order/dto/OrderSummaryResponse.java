package com.ondo.retail.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 내역의 한 줄. 통합 주문서 하나가 한 줄이다.
 *
 * @param wholesalerNames 화면에 "무드온 외 1곳" 으로 그린다
 * @param actionBadge     상태 이름 대신 지금 할 일을 준다
 */
public record OrderSummaryResponse(
        Long orderId,
        String orderNo,
        OffsetDateTime orderedAt,
        int totalAmount,
        int wholesalerCount,
        List<String> wholesalerNames,
        int totalQty,
        int receivedQty,
        int backorderQty,
        ActionBadge actionBadge) {
}
