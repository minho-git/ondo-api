package com.ondo.wholesale.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** listing_variant 의 복합 PK (listing_id, variant_id). 대리키가 없는 테이블이다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ListingVariantId implements Serializable {

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    public ListingVariantId(Long listingId, Long variantId) {
        this.listingId = listingId;
        this.variantId = variantId;
    }
}
