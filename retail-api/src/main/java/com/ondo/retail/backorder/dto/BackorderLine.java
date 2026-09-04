package com.ondo.retail.backorder.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 도매가 아는 만큼의 미송 한 줄 (MUL-97). {@link BackorderResponse} 가 되기 전 모양이다.
 *
 * <p><b>주문번호가 없다.</b> 화면에 찍히는 주문번호({@code 20260830-0930-0085})는 소매
 * 통합 주문서의 것이라 소매 DB 에만 있다. 도매가 주는 건 {@code orderId} 까지고,
 * {@code BackorderService} 가 그걸로 자기 DB 에서 번호를 채워 응답을 완성한다.
 *
 * <p>그래서 이 타입이 따로 있다. 클라이언트가 {@code BackorderResponse} 를 바로
 * 돌려주면 채울 수 없는 칸을 null 로 들고 다니게 되고, 그게 진짜 빈 값인지
 * 아직 안 채운 값인지 구분이 안 된다.
 *
 * @param orderId  소매 주문서 id. 도매의 {@code orders.retail_order_id} 다
 * @param qty      원래 미송량이고 안 변한다
 * @param listingId 도매가 상품을 지웠으면 null. 상품 상세로 못 넘어간다
 */
public record BackorderLine(
        Long backorderId,
        Long orderId,
        OffsetDateTime orderedAt,
        Wholesaler wholesaler,
        Long listingId,
        String title,
        String colorName,
        String size,
        int qty,
        LocalDate expectedInboundDate,
        String expectedInboundReason) {

    /** @param name 도매처 상호 */
    public record Wholesaler(Long id, String name) {}
}
