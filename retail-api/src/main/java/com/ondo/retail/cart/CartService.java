package com.ondo.retail.cart;

import com.ondo.retail.cart.domain.CartItem;
import com.ondo.retail.cart.dto.AddCartItemRequest;
import com.ondo.retail.cart.dto.AddCartItemResponse;
import com.ondo.retail.cart.dto.CartResponse;
import com.ondo.retail.cart.dto.ChangeQtyResponse;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.listing.ListingClient;
import com.ondo.retail.listing.dto.VariantInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    /** orderLimit 이 이 값이면 상한이 없다는 뜻이다. */
    private static final int UNLIMITED = 0;

    private final CartItemRepository cartItemRepository;
    private final ListingClient listingClient;

    /**
     * 담기. 이미 담은 옵션이면 수량을 더한다 — {@code UNIQUE (retailer_id, variant_id)} 라 행이 안 늘어난다.
     *
     * <p>더한 결과가 {@code orderLimit} 을 넘으면 막는다. 3장 담았고 상한이 5인데 4장을 더 담으면 400 이다.
     */
    @Transactional
    public AddCartItemResponse add(Long retailerId, AddCartItemRequest request) {
        VariantInfo variant = listingClient.findVariants(List.of(request.variantId()))
                .get(request.variantId());

        if (variant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!variant.orderable()) {
            throw new BusinessException(ErrorCode.LISTING_CLOSED);
        }

        CartItem item = cartItemRepository.findByRetailerIdAndVariantId(retailerId, request.variantId())
                .orElse(null);

        int finalQty = (item == null) ? request.qty() : item.getQty() + request.qty();
        checkOrderLimit(finalQty, variant.orderLimit());

        if (item == null) {
            item = cartItemRepository.save(CartItem.of(retailerId, request.variantId(), request.qty()));
        } else {
            item.addQty(request.qty());
        }

        log.info("장바구니 담기. retailerId={} variantId={} qty={}", retailerId, request.variantId(), finalQty);
        return new AddCartItemResponse(item.getId(), item.getQty(), cartItemRepository.countByRetailerId(retailerId));
    }

    /**
     * 조회. 도매처별로 묶는다.
     *
     * <p>담긴 건 {@code variantId} 와 수량뿐이라 상품 정보를 도매에서 한 번에 가져와 합친다.
     * 지워진 옵션은 정보가 안 오는데, 그때도 행은 남기고 주문 불가로 표시한다.
     */
    public CartResponse find(Long retailerId) {
        List<CartItem> items = cartItemRepository.findByRetailerIdOrderByCreatedAtDesc(retailerId);
        if (items.isEmpty()) {
            return new CartResponse(List.of(), 0, 0);
        }

        Map<Long, VariantInfo> variants = listingClient.findVariants(
                items.stream().map(CartItem::getVariantId).toList());

        // 도매처별로 묶는다. 정보가 없는 옵션은 도매처를 모르니 따로 모은다.
        Map<Long, List<CartResponse.Item>> byWholesaler = new LinkedHashMap<>();
        Map<Long, CartResponse.Wholesaler> wholesalers = new LinkedHashMap<>();
        List<CartResponse.Item> unknown = new ArrayList<>();

        for (CartItem item : items) {
            VariantInfo v = variants.get(item.getVariantId());
            if (v == null) {
                unknown.add(missingItem(item));
                continue;
            }
            wholesalers.putIfAbsent(v.wholesalerId(),
                    new CartResponse.Wholesaler(v.wholesalerId(), v.wholesalerName()));
            byWholesaler.computeIfAbsent(v.wholesalerId(), k -> new ArrayList<>())
                    .add(toItem(item, v));
        }

        List<CartResponse.Group> groups = new ArrayList<>();
        byWholesaler.forEach((wsId, list) -> groups.add(
                new CartResponse.Group(wholesalers.get(wsId), list, subtotal(list))));
        if (!unknown.isEmpty()) {
            groups.add(new CartResponse.Group(null, unknown, 0));
        }

        int totalQty = groups.stream().flatMap(g -> g.items().stream())
                .filter(CartResponse.Item::isOrderable)
                .mapToInt(CartResponse.Item::qty).sum();
        int totalAmount = groups.stream().mapToInt(CartResponse.Group::subtotal).sum();

        return new CartResponse(groups, totalQty, totalAmount);
    }

    /** 수량 변경. 더하는 게 아니라 그 값으로 교체한다. */
    @Transactional
    public ChangeQtyResponse changeQty(Long retailerId, Long cartItemId, int qty) {
        CartItem item = load(retailerId, cartItemId);

        VariantInfo variant = listingClient.findVariants(List.of(item.getVariantId()))
                .get(item.getVariantId());
        if (variant != null) {
            checkOrderLimit(qty, variant.orderLimit());
        }

        item.changeQty(qty);

        Integer salePrice = (variant == null) ? null : variant.salePrice();
        return new ChangeQtyResponse(item.getId(), qty,
                salePrice == null ? null : salePrice * qty);
    }

    /**
     * 장바구니에서 뺀다.
     *
     * <p><b>없는 id 여도 조용히 끝낸다.</b> 명세가 그렇다 — 결과가 같으면 같은 응답을 낸다.
     * 연타로 두 번 눌러도 두 번째가 에러로 뜨면 안 된다.
     *
     * <p>다만 <b>남의 항목은 404</b> 다. 여기서 403 을 내면 "그 id 가 존재한다" 는 게
     * 새어나간다. 그래서 없는 것과 남의 것을 여기서만 갈라 본다.
     */
    @Transactional
    public void remove(Long retailerId, Long cartItemId) {
        cartItemRepository.findById(cartItemId).ifPresent(item -> {
            if (!item.isOwnedBy(retailerId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            cartItemRepository.delete(item);
        });
    }

    public long count(Long retailerId) {
        return cartItemRepository.countByRetailerId(retailerId);
    }

    /**
     * 남의 장바구니를 못 건드리게 한다.
     *
     * <p>없는 것과 남의 것을 구분해 알려주지 않는다. 나누면 어떤 id 가 존재하는지 떠볼 수 있다.
     */
    private CartItem load(Long retailerId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(it -> it.isOwnedBy(retailerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void checkOrderLimit(int qty, Integer orderLimit) {
        if (orderLimit == null || orderLimit == UNLIMITED) {
            return;
        }
        if (qty > orderLimit) {
            throw new BusinessException(ErrorCode.ORDER_LIMIT_EXCEEDED,
                    "최대 " + orderLimit + "장까지 담을 수 있어요");
        }
    }

    private static CartResponse.Item toItem(CartItem item, VariantInfo v) {
        Integer lineAmount = (v.salePrice() == null) ? null : v.salePrice() * item.getQty();
        return new CartResponse.Item(item.getId(), item.getVariantId(), v.listingId(),
                v.title(), v.thumbnailUrl(), v.colorName(), v.size(),
                item.getQty(), v.salePrice(), lineAmount, v.orderLimit(), v.orderable());
    }

    /** 도매가 옵션을 지운 경우. 행은 남기되 주문에서 뺀다. */
    private static CartResponse.Item missingItem(CartItem item) {
        return new CartResponse.Item(item.getId(), item.getVariantId(), null,
                null, null, null, null, item.getQty(), null, null, null, false);
    }

    private static int subtotal(List<CartResponse.Item> items) {
        return items.stream()
                .filter(CartResponse.Item::isOrderable)
                .filter(it -> it.lineAmount() != null)
                .mapToInt(CartResponse.Item::lineAmount)
                .sum();
    }
}
