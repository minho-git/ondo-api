package com.ondo.retail.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주문서. 장바구니에서 고른 것만 넘겨 화면 하나를 그리는 데 필요한 걸 한 번에 받는다.
 *
 * <p>장바구니 조회와 모양이 거의 같고 계좌가 더 붙는다.
 *
 * <p><b>단가를 여기서 다시 받는다.</b> 담아둔 사이에 도매가 가격을 올렸으면 주문서에
 * 그게 반영돼야 한다. 장바구니 화면 금액과 다를 수 있다.
 *
 * @param groups      도매처별 묶음. 도매처마다 결제·수령을 따로 고른다
 * @param totalQty    전체 수량 합
 * @param totalAmount 전체 금액 합
 */
public record CheckoutResponse(List<Group> groups, int totalQty, int totalAmount) {


    /**
     * @param wholesaler 도매처와 입금 계좌
     * @param items      이 도매처에 주문할 것들
     * @param subtotal   도매처별 소계. 도매처마다 따로 입금한다
     */
    @Schema(name = "CheckoutGroup")
    public record Group(WholesalerWithBank wholesaler, List<Item> items, int subtotal) {
    }

    /**
     * @param cartItemId 주문 접수에 그대로 넘긴다
     * @param variantId  색상·사이즈 조합(SKU) id
     * @param title      상품명
     * @param colorName  색상
     * @param size       사이즈
     * @param qty        주문 수량
     * @param salePrice  지금 단가. 장바구니에 담을 때와 다를 수 있다
     * @param lineAmount qty × salePrice
     */
    @Schema(name = "CheckoutItem")
    public record Item(
            Long cartItemId,
            Long variantId,
            String title,
            String colorName,
            String size,
            int qty,
            int salePrice,
            int lineAmount) {
    }
}
