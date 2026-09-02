package com.ondo.retail.backorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 미송 대기 한 줄. 주문했는데 물건이 모자라서 아직 못 받은 것이다.
 *
 * <p>{@code qty} 는 <b>원래 미송량이고 안 변한다.</b> 배분이 돼서 잔여가 줄어도 이 값은 그대로다.
 * 잔여를 보려면 주문 상세를 봐야 한다.
 *
 * @param orderedAt             주문 시각. 목록의 정렬 기준이다 — 오래된 순으로 풀린다
 * @param expectedInboundDate   도매가 적어둔 예상 입고일. 아직 안 적었으면 null
 * @param expectedInboundReason 사유. 없으면 null
 */
public record BackorderResponse(
        Long backorderId,
        Long orderId,
        String orderNo,
        OffsetDateTime orderedAt,
        Wholesaler wholesaler,
        Long listingId,
        String title,
        String colorName,
        String size,
        int qty,
        LocalDate expectedInboundDate,
        String expectedInboundReason) {

    @Schema(name = "BackorderWholesaler")
    public record Wholesaler(Long id, String name) {
    }
}
