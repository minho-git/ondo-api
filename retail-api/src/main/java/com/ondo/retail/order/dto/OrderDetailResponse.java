package com.ondo.retail.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 통합 주문서 하나를 펼친다. 상태를 세 층으로 보여준다.
 *
 * <pre>
 * 층 1  통합 주문서    할 일 뱃지 + 장 수      주문 내역 화면
 * 층 2  도매처별 주문   도매가 쓰는 상태 이름     여기
 * 층 3  품목 줄        수량으로 말한다          주문 5 · 받음 3 · 미송 2
 * </pre>
 *
 * @param orderId          통합 주문서 id
 * @param orderNo          화면에 보여주는 주문번호
 * @param orderedAt        주문 시각
 * @param totalAmount      이 주문 전체 금액
 * @param agentName        사입삼촌. 도매처가 여럿이어도 사람은 하나다
 * @param agentPhone       사입삼촌 연락처
 * @param wholesalerOrders 도매처별 주문. 상태도 취소도 여기 단위로 움직인다
 */
public record OrderDetailResponse(
        Long orderId,
        String orderNo,
        OffsetDateTime orderedAt,
        int totalAmount,
        String agentName,
        String agentPhone,
        List<WholesalerOrder> wholesalerOrders) {


    /**
     * @param wholesaleOrderId 취소할 때 보내는 값
     * @param orderNumber      도매처별 연번
     * @param wholesaler       도매처와 입금 계좌
     * @param status           도매처별 주문 상태
     * @param paymentTerm      고른 결제 조건
     * @param receiveMethod    고른 수령 방법
     * @param amount           이 도매처 금액
     * @param isCancellable    status.key 가 NEW 일 때만 true
     * @param items            품목 줄
     * @param outbounds        출고 기록. 아직 없으면 빈 배열
     */
    public record WholesalerOrder(
            Long wholesaleOrderId,
            Integer orderNumber,
            WholesalerWithBank wholesaler,
            Status status,
            PaymentTerm paymentTerm,
            ReceiveMethod receiveMethod,
            int amount,
            boolean isCancellable,
            List<Item> items,
            List<Outbound> outbounds) {
    }

    /**
     * 도매처별 주문 상태.
     *
     * <p>{@code key} 는 NEW / CONFIRMED / PARTIALLY_SHIPPED / SHIPPED / CANCELLED 다.
     * <b>저장값이 아니라 파생값이다</b> — DB 엔 NEW · CONFIRMED · CANCELLED 세 값뿐이고
     * 출고 진행도를 합쳐 다섯이 된다.
     *
     * <p>{@code label} 도 도매가 주는 걸 그대로 쓴다. 같은 주문을 두 화면이 다르게 부르면 안 된다.
     *
     * @param key   분기에 쓰는 코드
     * @param label 화면에 그대로 쓰는 한글 이름
     */
    public record Status(String key, String label) {
    }

    /**
     * @param listingId           상품 상세로 넘어갈 때 쓴다
     * @param title               상품명
     * @param colorName           색상
     * @param size                사이즈
     * @param qty                 주문한 수량
     * @param unitPrice           주문 시점 스냅샷. 현재 판매가와 다를 수 있다
     * @param lineAmount          qty × unitPrice
     * @param receivedQty         받은 수량
     * @param backorderQty        아직 못 받은 수량
     * @param expectedInboundDate 미송 예상 입고일. SKU 단위라 같은 옵션을 기다리는 소매처들은 같은 날짜를 본다
     */
    @Schema(name = "OrderItem")
    public record Item(
            Long listingId,
            String title,
            String colorName,
            String size,
            int qty,
            int unitPrice,
            int lineAmount,
            int receivedQty,
            int backorderQty,
            LocalDate expectedInboundDate) {
    }

    /**
     * 출고 기록. <b>장끼 단위</b>라 주문 하나가 여러 장끼로 나뉠 수 있다.
     *
     * <p>장끼 한 장에 다른 주문이 섞일 수도 있다 — 출고가 거래처 단위라서다.
     * 그래서 여기엔 이 주문 몫만 적는다.
     *
     * @param outboundId      출고 id
     * @param statementNumber 장끼 번호. 매장에서 이걸로 찾는다
     * @param shippedAt       null 이면 포장만 끝난 상태다
     * @param items           이 장끼에 실린 이 주문 몫
     */
    public record Outbound(
            Long outboundId,
            String statementNumber,
            OffsetDateTime shippedAt,
            List<OutboundItem> items) {
    }
    /**
     * @param title     상품명
     * @param colorName 색상
     * @param size      사이즈
     * @param qty       이 장끼에 실린 수량
     */

    public record OutboundItem(String title, String colorName, String size, int qty) {
    }
}
