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
 * @param backorderId           미송 id
 * @param orderId               어느 주문에서 나왔는지. 주문 상세로 넘어갈 때 쓴다
 * @param orderNo               화면에 보여주는 주문번호
 * @param orderedAt             주문 시각. 목록의 정렬 기준이다 — 오래된 순으로 풀린다
 * @param wholesaler            이 미송이 걸린 도매처
 * @param listingId             상품 상세로 넘어갈 때 쓴다
 * @param title                 상품명
 * @param colorName             색상
 * @param size                  사이즈
 * @param qty                   미송 수량
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

    /**
     * @param id   도매처 id
     * @param name 도매처 상호
     */
    @Schema(name = "BackorderWholesaler")
    public record Wholesaler(Long id, String name) {
    }
}
