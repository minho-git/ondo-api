package com.ondo.retail.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 한 줄.
 *
 * <p>담는 건 {@code variantId} 와 수량뿐이다. 상품명 · 이미지 · 단가는 전부 도매 것이라
 * 조회할 때마다 가져온다. 담아둔 사이에 도매가 가격을 올렸으면 장바구니에서 바뀌어 보인다.
 *
 * <p>{@code variant_id} 에 FK 가 없다. DB 가 도매·소매로 갈라져 있어서 걸 수 없다 —
 * 지워진 옵션을 가리킬 수 있고, 그건 조회할 때 "주문 불가" 로 처리한다.
 *
 * <p>{@code UNIQUE (retailer_id, variant_id)} — 같은 옵션은 항상 한 행이다.
 * 이미 담은 걸 또 담으면 행이 늘지 않고 수량이 더해진다.
 */
@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retailer_id", nullable = false)
    private Long retailerId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int qty;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static CartItem of(Long retailerId, Long variantId, int qty) {
        CartItem it = new CartItem();
        it.retailerId = retailerId;
        it.variantId = variantId;
        it.qty = qty;
        it.createdAt = OffsetDateTime.now();
        it.updatedAt = it.createdAt;
        return it;
    }

    /** 이미 담은 옵션을 또 담을 때. 더한다. */
    public void addQty(int amount) {
        this.qty += amount;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 수량 변경. 더하는 게 아니라 그 값으로 바꾼다. */
    public void changeQty(int newQty) {
        this.qty = newQty;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isOwnedBy(Long retailerId) {
        return this.retailerId.equals(retailerId);
    }
}
