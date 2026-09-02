package com.ondo.retail.order.dto;

import java.util.List;

/**
 * 주문 취소.
 *
 * <p><b>도매처별로 취소한다.</b> 통합 주문서를 통째 취소하는 건 없다 —
 * 한쪽은 이미 확정됐을 수 있다.
 *
 * @param wholesaleOrderIds 취소할 도매처 건. 생략하면 취소 가능한 것 전부
 */
public record CancelOrderRequest(List<Long> wholesaleOrderIds) {
}
