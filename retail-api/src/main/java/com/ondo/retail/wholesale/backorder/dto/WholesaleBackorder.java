package com.ondo.retail.wholesale.backorder.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 도매가 내려주는 미송 한 줄 그대로 (MUL-97).
 *
 * <p>소매 DTO 와 모양이 거의 같지만 <b>타입은 다르다.</b> 채빈이 도매에서 필드를 바꾸면
 * {@code WholesaleBackorderAdapter} 가 컴파일 에러로 먼저 막는다 — 상품(MUL-88)에서와 같은
 * 이유다. 조용히 프론트까지 흘러가면 창은이 화면이 이유 없이 깨진다.
 *
 * <p>도매는 {@code retailOrderId} 라는 이름으로 준다. 도매 입장에서는 "남의 주문서 id" 라
 * 그렇게 부르지만, 소매에 들어오면 그냥 자기 주문서 id 다.
 */
public record WholesaleBackorder(
        Long backorderId,
        Long retailOrderId,
        OffsetDateTime orderedAt,
        Wholesaler wholesaler,
        Long listingId,
        String title,
        String colorName,
        String size,
        int qty,
        LocalDate expectedInboundDate,
        String expectedInboundReason) {

    public record Wholesaler(Long id, String name) {}
}
