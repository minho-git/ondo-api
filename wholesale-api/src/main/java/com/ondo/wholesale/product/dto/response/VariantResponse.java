package com.ondo.wholesale.product.dto.response;

import com.ondo.wholesale.product.domain.Size;

import java.math.BigDecimal;

/**
 * SKU 하나. {@code availableQty = stockQty − allocatedQty} (미송은 빼지 않는다).
 * {@code salePrice}·{@code orderLimit}은 게시글이 없을 때만 {@code null},
 * {@code orderLimit} {@code 0} = 무제한.
 */
public record VariantResponse(
        Long id,
        Integer variantNumber,
        Size size,
        int stockQty,
        int allocatedQty,
        int backorderQty,
        int availableQty,
        BigDecimal avgCost,
        Integer salePrice,
        Integer orderLimit
) {}
