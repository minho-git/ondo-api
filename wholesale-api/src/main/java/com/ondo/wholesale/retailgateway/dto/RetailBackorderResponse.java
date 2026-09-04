package com.ondo.wholesale.retailgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 소매 미송 목록의 한 줄 (MUL-97).
 *
 * <p><b>주문번호를 안 싣는다.</b> 소매 화면이 보여주는 주문번호({@code 20260830-0930-0085})는
 * 소매 통합 주문서의 것이고 도매에는 그 값이 없다. 도매의 {@code orders.order_number} 는
 * 도매처별 연번이라 다른 번호다. 도매가 아는 건 {@code retailOrderId} 까지고,
 * 소매가 이걸로 자기 DB 에서 번호를 채운다.
 *
 * @param qty                 원래 미송량이고 <b>안 변한다.</b> 배분이 돼서 잔여가 줄어도 이 값은 그대로다
 * @param listingId           게시글이 지워졌으면 null. 소매가 상품 상세 링크를 못 건다
 * @param title               게시글 제목. 게시글이 지워졌으면 품명으로 대신한다
 * @param expectedInboundDate 도매가 SKU 에 적어둔 예상 입고일. 안 적었으면 null
 */
public record RetailBackorderResponse(
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

    /** 미송 목록에서는 id·상호만 쓴다. 매장 위치는 상품 상세에서 준다. */
    @Schema(name = "RetailGatewayBackorderWholesaler")
    public record Wholesaler(Long id, String name) {}
}
