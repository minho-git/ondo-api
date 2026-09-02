package com.ondo.retail.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 내역의 한 줄. 통합 주문서 하나가 한 줄이다.
 *
 * @param orderId         주문 상세를 부를 때 쓴다
 * @param orderNo         화면에 보여주는 주문번호
 * @param orderedAt       주문 시각. 목록의 정렬 기준이다
 * @param totalAmount     이 주문 전체 금액
 * @param wholesalerCount 도매처 수
 * @param wholesalerNames 화면에 "무드온 외 1곳" 으로 그린다
 * @param totalQty        주문한 총 장 수
 * @param receivedQty     그중 받은 장 수
 * @param backorderQty    아직 못 받은 장 수
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
