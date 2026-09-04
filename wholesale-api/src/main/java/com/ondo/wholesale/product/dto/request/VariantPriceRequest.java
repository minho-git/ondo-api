package com.ondo.wholesale.product.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ondo.wholesale.product.domain.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * variant 하나의 판매가·주문 제한.
 *
 * <p>variant 지정은 두 방식 중 하나만 쓴다 — 기존 variant 는 {@code variantId},
 * 아직 id 가 없는 신규 variant 는 {@code (colorId, size)}. 등록(POST)은 전부 신규라
 * 항상 후자다. 둘 다 채우거나 둘 다 비우면 400 (PATCH 계약서).
 */
public record VariantPriceRequest(
        @Schema(example = "null", description = "기존 variant 지정용 (PATCH)")
        Long variantId,

        @Schema(example = "1")
        Long colorId,

        @Schema(example = "S")
        Size size,

        @Schema(example = "29000")
        @NotNull @Min(0)
        Integer salePrice,

        @Schema(example = "0", description = "1회 주문당 최대 장수. 0 = 무제한")
        @Min(0)
        Integer orderLimit
) {
    public VariantPriceRequest {
        orderLimit = (orderLimit == null) ? 0 : orderLimit;
    }

    /** 지정 방식은 정확히 하나 — variantId 단독 또는 (colorId, size) 쌍. */
    @JsonIgnore
    @AssertTrue(message = "variantId 또는 (colorId, size) 중 한쪽만 지정한다.")
    @Schema(hidden = true)
    public boolean isTargetSpecified() {
        boolean byId = variantId != null && colorId == null && size == null;
        boolean byColorSize = variantId == null && colorId != null && size != null;
        return byId || byColorSize;
    }
}
