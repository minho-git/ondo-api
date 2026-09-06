package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.domain.PackingItem;
import com.ondo.wholesale.order.dto.response.PackingCreatedResponse;
import com.ondo.wholesale.order.dto.response.PackingItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 포장 카드 응답 조립 (MUL-47). 항목의 qty 는 배분 수량이지 출고 수량이 아니다 —
 * 그 구분은 DTO 자바독이 계약으로 못박고 있다.
 */
@Component
@RequiredArgsConstructor
public class PackingAssembler {

    private final VariantInfoReader variantInfoReader;

    public PackingCreatedResponse created(Packing packing, List<OrderItem> orderItems) {
        return new PackingCreatedResponse(
                packing.getId(), packing.getOrderId(), packing.getStatus(),
                packing.getOutboundId(), packing.getCreatedAt(),
                itemRows(packing.getItems(), orderItems));
    }

    /** 살아있는 항목만 — deleted_at 이 찍힌 항목은 배분취소된 것이라 카드에 없다. */
    public List<PackingItemResponse> itemRows(List<PackingItem> items, List<OrderItem> orderItems) {
        Map<Long, OrderItem> byId = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<PackingItem> alive = items.stream().filter(i -> i.getDeletedAt() == null).toList();
        Map<Long, VariantInfoReader.VariantInfo> variants = variantInfoReader.read(
                alive.stream().map(i -> byId.get(i.getOrderItemId()).getVariantId()).distinct().toList());
        return alive.stream().map(item -> {
            OrderItem orderItem = byId.get(item.getOrderItemId());
            VariantInfoReader.VariantInfo variant = variants.get(orderItem.getVariantId());
            return new PackingItemResponse(
                    item.getId(), item.getOrderItemId(), orderItem.getVariantId(),
                    variant.productNumber(), variant.variantNumber(), variant.productName(),
                    variant.colorName(), variant.size(), item.getQty());
        }).toList();
    }
}
