package com.ondo.retail.wholesale.order.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 도매가 내려주는 주문 하나 그대로 (MUL-98).
 *
 * <p>소매 주문서 하나에 이게 도매처 수만큼 온다. {@code retailOrderId} 로 묶는다.
 *
 * <p>{@code statusKey}·{@code statusLabel} 은 도매가 만들어 준다. DB 는 세 값만 저장하고
 * 출고 진행도를 합쳐 다섯이 되는데, 그 규칙을 소매가 또 짜면 두 화면이 같은 주문을
 * 다르게 부른다.
 */
public record WholesaleOrderView(
        Long orderId,
        Long retailOrderId,
        Integer orderNumber,
        OffsetDateTime orderedAt,
        Wholesaler wholesaler,
        String statusKey,
        String statusLabel,
        String paymentTerm,
        String receiveMethod,
        String agentName,
        String agentPhone,
        int amount,
        boolean cancellable,
        List<Item> items) {

    public record Wholesaler(Long id, String name, String storeBuilding, String storeUnit,
                             String bankName, String bankAccountNo, String bankAccountHolder) {}

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
}
