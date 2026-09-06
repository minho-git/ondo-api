package com.ondo.retail.order.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 도매처 주문 하나 (MUL-98). 소매 주문서 하나에 이게 여럿 딸린다.
 *
 * <p>{@link OrderSummaryResponse}·{@link OrderDetailResponse} 가 되기 전 모양이다.
 * 내역은 이걸 주문서별로 합쳐 장 수를 세고, 상세는 거의 그대로 펼친다.
 *
 * <p>상태는 도매가 만들어 준 걸 그대로 쓴다. 소매가 규칙을 또 짜면 두 화면이 같은
 * 주문을 다르게 부른다.
 */
public record OrderView(
        Long retailOrderId,
        Long wholesaleOrderId,
        Integer orderNumber,
        WholesalerWithBank wholesaler,
        String statusKey,
        String statusLabel,
        PaymentTerm paymentTerm,
        ReceiveMethod receiveMethod,
        int amount,
        boolean cancellable,
        List<Item> items) {

    /** @param backorderQty 아직 못 받은 수량. 주문 수량에서 받은 수량을 뺀 것이다 */
    public record Item(
            Long listingId,
            String title,
            String colorName,
            String size,
            int qty,
            int unitPrice,
            int receivedQty,
            int backorderQty,
            LocalDate expectedInboundDate) {}
}
