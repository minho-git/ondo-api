package com.ondo.wholesale.retailgateway.dto;

import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 소매 주문서 하나에 딸린 도매처 주문 (MUL-98).
 *
 * <p>소매는 주문서 하나로 받고 도매처별로 잘라 넣는다. 그래서 소매 화면이 주문 하나를
 * 펼치면 <b>도매처 수만큼의 이 응답</b>이 필요하다. {@code retailOrderId} 로 묶인다.
 *
 * <p><b>상태를 도매가 만들어 내린다.</b> DB 는 {@code NEW}·{@code CONFIRMED}·{@code CANCELLED}
 * 셋뿐이고 출고 진행도를 합쳐 다섯이 된다. 그 조합 규칙은 도매 안에만 두기로 했고
 * (채빈 {@code OrderStatusRule}), 소매가 같은 규칙을 또 짜면 두 화면이 같은 주문을
 * 다르게 부르게 된다. 라벨까지 같이 주는 이유도 같다.
 *
 * @param statusKey   NEW · CONFIRMED · PARTIALLY_SHIPPED · SHIPPED · CANCELLED
 * @param statusLabel 화면에 그대로 쓰는 한글 이름
 * @param cancellable 소매가 취소 버튼을 켤지. {@code NEW} 일 때만 참이다
 */
public record RetailOrderViewResponse(
        Long orderId,
        Long retailOrderId,
        Integer orderNumber,
        OffsetDateTime orderedAt,
        Wholesaler wholesaler,
        String statusKey,
        String statusLabel,
        PaymentMethod paymentTerm,
        ReceiveBy receiveMethod,
        String agentName,
        String agentPhone,
        int amount,
        boolean cancellable,
        List<Item> items) {

    /** 주문서 화면이 쓸 도매처. 입금 계좌가 필요해 상품 목록의 것보다 넓다. */
    @Schema(name = "RetailOrderWholesaler")
    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit,
                             String bankName, String bankAccountNo, String bankAccountHolder) {}

    /**
     * 품목 한 줄. <b>수량으로 말한다</b> — 주문 5 · 받음 3 · 미송 2.
     *
     * @param unitPrice           주문 시점 스냅샷. 지금 판매가와 다를 수 있다
     * @param receivedQty         실제로 나간 수량
     * @param backorderQty        아직 못 받은 수량. 열려 있는 미송의 합이다
     * @param expectedInboundDate 미송 예상 입고일. SKU 에 붙는 값이라 같은 옵션을
     *                            기다리는 소매처들은 같은 날짜를 본다 (D-067)
     * @param listingId           상품 상세로 넘어갈 때 쓴다. 게시글이 지워졌으면 null
     */
    @Schema(name = "RetailOrderItem")
    public record Item(
            Long orderItemId,
            Long variantId,
            Long listingId,
            String title,
            String colorName,
            String size,
            int qty,
            int unitPrice,
            int receivedQty,
            int backorderQty,
            LocalDate expectedInboundDate) {}

    /** 도매 표시 상태를 문자열로 옮긴다. 소매가 enum 을 또 갖지 않게 한다. */
    public static String keyOf(OrderStatusKey key) {
        return key.name();
    }
}
