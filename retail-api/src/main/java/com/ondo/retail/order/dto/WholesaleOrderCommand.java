package com.ondo.retail.order.dto;

import java.util.List;

/**
 * 도매처 한 곳에 넣을 주문 (MUL-98).
 *
 * <p>소매의 통합 주문서를 도매처별로 자른 조각이다. 도매의 원자성 단위가 도매처별
 * 주문이라, 주문서에 도매처가 셋이면 이 명령이 셋 나간다.
 *
 * @param retailOrderId 소매 통합 주문서 id. 도매가 이 값으로 중복을 막는다
 * @param retailerName  소매 상호. 도매가 소매 DB 를 못 읽어서 실어 보낸다.
 *                      도매의 거래처 상호가 NOT NULL 이라 <b>없으면 접수가 실패한다</b>
 */
public record WholesaleOrderCommand(
        Long retailOrderId,
        Long retailerId,
        Long wholesalerId,
        String retailerName,
        String retailerPhone,
        String paymentTerm,
        String receiveMethod,
        String agentName,
        String agentPhone,
        List<Line> items) {

    /**
     * @param expectedUnitPrice 소매 화면이 보여준 값. 도매가 지금 값과 대조해
     *                          어긋나면 거절한다 — 사용자가 본 금액과 청구가 달라지지 않게
     */
    public record Line(Long variantId, int qty, int expectedUnitPrice) {}
}
