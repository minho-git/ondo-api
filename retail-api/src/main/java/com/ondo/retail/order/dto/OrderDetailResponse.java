package com.ondo.retail.order.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 통합 주문서 하나를 펼친다. 상태를 세 층으로 보여준다.
 *
 * <pre>
 * 층 1  통합 주문서    할 일 뱃지 + 장 수      주문 내역 화면
 * 층 2  도매처별 주문   도매가 쓰는 상태 이름     여기
 * 층 3  품목 줄        수량으로 말한다          주문 5 · 받음 3 · 미송 2
 * </pre>
 *
 * @param agentName 사입삼촌. 도매처가 여럿이어도 사람은 하나다
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
     * @param orderNumber 도매처별 연번
     * @param isCancellable status.key 가 NEW 일 때만 true
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
     */
    public record Status(String key, String label) {
    }

    /**
     * @param unitPrice           주문 시점 스냅샷. 현재 판매가와 다를 수 있다
     * @param expectedInboundDate 미송 예상 입고일. SKU 단위라 같은 옵션을 기다리는
     *                            소매처들은 같은 날짜를 본다
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
     * @param shippedAt null 이면 포장만 끝난 상태다
     */
    public record Outbound(
            Long outboundId,
            String statementNumber,
            OffsetDateTime shippedAt,
            List<OutboundItem> items) {
    }

    public record OutboundItem(String title, String colorName, String size, int qty) {
    }
}
