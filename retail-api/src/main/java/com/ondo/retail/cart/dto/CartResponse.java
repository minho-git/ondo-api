package com.ondo.retail.cart.dto;

import java.util.List;

/**
 * 장바구니 전체. <b>도매처별로 묶어서 내린다</b> — 화면이 그렇게 생겼고 주문도 도매처 단위로 쪼개진다.
 */
public record CartResponse(List<Group> groups, int totalQty, int totalAmount) {

    public record Group(Wholesaler wholesaler, List<Item> items, int subtotal) {
    }

    public record Wholesaler(Long id, String name) {
    }

    /**
     * @param salePrice   매번 도매에서 다시 가져온다. 주문 불가면 마지막 값이거나 null
     * @param lineAmount  {@code qty × salePrice}
     * @param orderLimit  1회 주문당 최대. {@code 0} 이면 무제한
     * @param isOrderable 담아둔 사이에 시즌이 끝나거나 옵션이 지워졌으면 false.
     *                    행은 남으니 회색으로 그리고 주문에서 뺀다
     */
    public record Item(
            Long cartItemId,
            Long variantId,
            Long listingId,
            String title,
            String thumbnailUrl,
            String colorName,
            String size,
            int qty,
            Integer salePrice,
            Integer lineAmount,
            Integer orderLimit,
            boolean isOrderable) {
    }
}
