package com.ondo.wholesale.order.dto.response;

import com.ondo.wholesale.product.domain.Size;

/**
 * 주문 라인 하나. 수량 필드의 스코프에 주의 — 접두사 없는 값은 이 라인의 것이고,
 * {@code variantAvailableQty}만 SKU 전체 스코프다(화면 "가용재고(미송)"의 앞 숫자).
 * {@code unitPrice}는 주문 시점 스냅샷이라 현재 판매가와 다를 수 있다.
 */
public record OrderItemResponse(
        Long id,
        Long variantId,
        Integer productNumber,
        Integer variantNumber,
        String productName,
        String color,
        Size size,
        int unitPrice,
        int qty,
        int allocatedQty,
        int shippedQty,
        int unallocatedQty,
        int variantAvailableQty,
        int backorderQty
) {}
