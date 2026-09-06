package com.ondo.retail.order;

import com.ondo.retail.cart.CartItemRepository;
import com.ondo.retail.cart.domain.CartItem;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.listing.ListingClient;
import com.ondo.retail.listing.dto.VariantInfo;
import com.ondo.retail.order.dto.CheckoutResponse;
import com.ondo.retail.order.dto.WholesalerWithBank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문서 (MUL-98).
 *
 * <p>장바구니에서 고른 줄만 도매처별로 묶어 보여준다. 장바구니 조회와 모양이 거의 같고
 * <b>입금 계좌</b>가 더 붙는다 — 도매처마다 따로 입금하는 화면이라서다.
 *
 * <p><b>단가를 여기서 다시 읽는다.</b> 담아둔 사이 도매가 가격을 올렸으면 주문서에 그게
 * 반영돼야 한다. 장바구니 화면 금액과 다를 수 있고, 그게 맞다 — 접수할 때 도매가
 * 이 값으로 대조하기 때문에 여기서 보여준 금액이 실제 청구 금액이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutService {

    private final CartItemRepository cartItemRepository;
    private final ListingClient listingClient;
    private final WholesalerClient wholesalerClient;

    public CheckoutResponse checkout(Long retailerId, List<Long> cartItemIds) {
        List<CartItem> items = loadCartItems(retailerId, cartItemIds);
        Map<Long, VariantInfo> variants = listingClient.findVariants(
                items.stream().map(CartItem::getVariantId).toList());

        Map<Long, List<CheckoutResponse.Item>> byWholesaler = new LinkedHashMap<>();
        for (CartItem item : items) {
            VariantInfo v = variants.get(item.getVariantId());
            // 담아둔 사이 도매가 지웠거나 내린 옵션이다. 주문서에 올리면 접수에서 튕긴다
            if (v == null || !v.orderable()) {
                throw new BusinessException(ErrorCode.UNORDERABLE_ITEM_INCLUDED);
            }
            byWholesaler.computeIfAbsent(v.wholesalerId(), k -> new ArrayList<>())
                    .add(toItem(item, v));
        }

        Map<Long, WholesalerWithBank> wholesalers =
                wholesalerClient.findAll(List.copyOf(byWholesaler.keySet()));

        List<CheckoutResponse.Group> groups = new ArrayList<>();
        byWholesaler.forEach((wholesalerId, lines) -> groups.add(new CheckoutResponse.Group(
                wholesalers.get(wholesalerId), lines, subtotal(lines))));

        int totalQty = groups.stream().flatMap(g -> g.items().stream())
                .mapToInt(CheckoutResponse.Item::qty).sum();
        int totalAmount = groups.stream().mapToInt(CheckoutResponse.Group::subtotal).sum();

        return new CheckoutResponse(groups, totalQty, totalAmount);
    }

    // ── 거들기 ─────────────────────────────────────────────────

    private List<CartItem> loadCartItems(Long retailerId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<CartItem> items = cartItemRepository.findAllById(cartItemIds).stream()
                .filter(item -> item.isOwnedBy(retailerId))
                .toList();

        // 남의 줄이나 이미 지워진 줄이 섞이면 주문서 금액이 실제와 달라진다
        if (items.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return items;
    }

    private static CheckoutResponse.Item toItem(CartItem item, VariantInfo v) {
        return new CheckoutResponse.Item(
                item.getId(), v.variantId(), v.title(), v.colorName(), v.size(),
                item.getQty(), v.salePrice(), item.getQty() * v.salePrice());
    }

    private static int subtotal(List<CheckoutResponse.Item> lines) {
        return lines.stream().mapToInt(CheckoutResponse.Item::lineAmount).sum();
    }
}
