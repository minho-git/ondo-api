package com.ondo.retail.wholesale.order.dto;

import java.util.List;

/**
 * 도매 소매접점 주문 생성 요청 그대로 (MUL-98).
 *
 * <p>소매 말({@code WholesaleOrderCommand})과 모양이 거의 같지만 타입이 다르다.
 * 채빈이 계약을 바꾸면 어댑터가 컴파일 에러로 먼저 막는다 — 상품·미송에서와 같은 이유다.
 */
public record WholesaleOrderCreateRequest(
        Long retailOrderId,
        Long retailerId,
        Long wholesalerId,
        String retailerName,
        String retailerPhone,
        String expectedPaymentMethod,
        String receiveBy,
        String agentName,
        String agentPhone,
        List<Item> items) {

    public record Item(Long variantId, Integer qty, Integer expectedUnitPrice) {}
}
