package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.order.dto.response.OrderDetailResponse;
import com.ondo.wholesale.order.dto.response.OrderItemResponse;
import com.ondo.wholesale.order.dto.response.OrderStatusResponse;
import com.ondo.wholesale.order.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 주문 상세 응답 조립 (MUL-47).
 *
 * <p>라인은 엔티티에서, 표시용 변형 정보(품번·상품명·색상·사이즈·가용재고)는 jdbc 배치
 * 1방에서, OPEN 미송 여부는 배치 1방에서 온다. 수량 파생은 라인 스코프고
 * variantAvailableQty(재고 − 예약)만 SKU 스코프라는 계약을 여기서 지킨다.
 */
@Component
@RequiredArgsConstructor
public class OrderDetailAssembler {

    private final PartnerRepository partnerRepository;
    private final OrderSummaryReader reader;
    private final VariantInfoReader variantInfoReader;
    private final NamedParameterJdbcTemplate jdbc;

    public OrderDetailResponse assemble(Order order) {
        Partner partner = partnerRepository.findById(order.getPartnerId()).orElseThrow();
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getId)).toList();

        Map<Long, VariantInfoReader.VariantInfo> variants = variantInfoReader.read(
                items.stream().map(OrderItem::getVariantId).distinct().toList());
        Set<Long> openBackorderItemIds = openBackorderItemIds(
                items.stream().map(OrderItem::getId).toList());
        OrderSummaryReader.Settlement settlement =
                reader.settlements(List.of(order.getId())).get(order.getId());

        int totalQty = items.stream().mapToInt(OrderItem::getQty).sum();
        int allocatedSum = items.stream().mapToInt(OrderItem::getAllocatedQty).sum();
        int shippedSum = items.stream().mapToInt(OrderItem::getShippedQty).sum();
        int orderAmount = items.stream().mapToInt(i -> i.getQty() * i.getUnitPrice()).sum();
        OrderStatusRule.Derived derived = OrderStatusRule.derive(order.getStatus(),
                totalQty, allocatedSum, shippedSum);

        List<OrderItemResponse> itemRows = items.stream()
                .map(item -> itemRow(item, variants.get(item.getVariantId()),
                        openBackorderItemIds.contains(item.getId())))
                .toList();

        return new OrderDetailResponse(
                order.getId(), order.getOrderNumber(), order.getOrderedAt(), order.getConfirmedAt(),
                partner.getRetailerId(), partner.getRetailerName(), partner.getRetailerPhone(),
                order.getPaymentTerm(), order.getReceiveMethod(),
                new OrderStatusResponse(derived.key(), derived.label()),
                settlement.status(),
                derived.confirmable(), derived.cancellable(), derived.packable(),
                orderAmount, totalQty, itemRows);
    }

    private OrderItemResponse itemRow(OrderItem item, VariantInfoReader.VariantInfo variant, boolean hasOpenBackorder) {
        int unallocated = item.getQty() - item.getAllocatedQty();
        return new OrderItemResponse(
                item.getId(), item.getVariantId(),
                variant.productNumber(), variant.variantNumber(),
                variant.productName(), variant.colorName(), variant.size(),
                item.getUnitPrice(), item.getQty(),
                item.getAllocatedQty(), item.getShippedQty(), unallocated,
                variant.availableQty(),
                hasOpenBackorder ? unallocated : 0);
    }


    private Set<Long> openBackorderItemIds(List<Long> orderItemIds) {
        return new HashSet<>(jdbc.queryForList("""
                select order_item_id from wholesale.backorder
                where order_item_id in (:ids) and status = 'OPEN'
                """, Map.of("ids", orderItemIds), Long.class));
    }
}
