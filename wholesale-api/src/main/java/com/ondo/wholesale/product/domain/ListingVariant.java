package com.ondo.wholesale.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 마켓에 올린 옵션의 판매 조건. 행이 있으면 = 올린 옵션이고 <b>지우지 않는다 (D-053)</b> —
 * variant 가 soft delete 돼도 행은 남고, 사이즈를 재추가하면 새 variant id 로 새 행이 생긴다.
 * sale_price 는 판매가의 유일한 저장 위치다.
 */
@Entity
@Table(name = "listing_variant", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingVariant {

    @EmbeddedId
    private ListingVariantId id;

    @Column(name = "sale_price", nullable = false)
    private int salePrice;

    /** 1회 주문당 최대 장수. 0 = 무제한 (D-057). */
    @Column(name = "order_limit", nullable = false)
    private int orderLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private ListingVariant(Long listingId, Long variantId, int salePrice, int orderLimit) {
        this.id = new ListingVariantId(listingId, variantId);
        this.salePrice = salePrice;
        this.orderLimit = orderLimit;
    }

    public void reprice(int salePrice, int orderLimit) {
        this.salePrice = salePrice;
        this.orderLimit = orderLimit;
    }
}
