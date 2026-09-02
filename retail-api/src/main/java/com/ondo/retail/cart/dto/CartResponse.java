package com.ondo.retail.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 장바구니 전체. <b>도매처별로 묶어서 내린다</b> — 화면이 그렇게 생겼고 주문도 도매처 단위로 쪼개진다.
 *
 * @param groups      도매처별 묶음
 * @param totalQty    전체 수량 합
 * @param totalAmount 전체 금액 합
 */
public record CartResponse(List<Group> groups, int totalQty, int totalAmount) {

    /**
     * @param wholesaler 이 묶음의 도매처
     * @param items      이 도매처에 담아둔 것들
     * @param subtotal   이 도매처 소계
     */
    @Schema(name = "CartGroup")
    public record Group(Wholesaler wholesaler, List<Item> items, int subtotal) {
    }
    /**
     * @param id   도매처 id
     * @param name 도매처 상호
     */
    @Schema(name = "CartWholesaler")
    public record Wholesaler(Long id, String name) {
    }

    /**
     * @param cartItemId   수량을 바꾸거나 뺄 때 보내는 값
     * @param variantId    색상·사이즈 조합(SKU) id
     * @param listingId    상품 상세로 넘어갈 때 쓴다
     * @param title        상품명
     * @param thumbnailUrl 대표 이미지
     * @param colorName    색상
     * @param size         사이즈
     * @param qty          담아둔 수량
     * @param salePrice    매번 도매에서 다시 가져온다. 주문 불가면 마지막 값이거나 null
     * @param lineAmount   {@code qty × salePrice}
     * @param orderLimit   1회 주문당 최대. {@code 0} 이면 무제한
     * @param isOrderable  담아둔 사이에 시즌이 끝나거나 옵션이 지워졌으면 false. 행은 남으니 회색으로 그리고 주문에서 뺀다
     */
    @Schema(name = "CartItem")
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
