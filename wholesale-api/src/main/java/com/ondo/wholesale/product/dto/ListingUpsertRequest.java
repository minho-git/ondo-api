package com.ondo.wholesale.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 게시글 본문. 등록(POST)에선 생성, 수정(PATCH)에선 upsert 로 쓰인다.
 *
 * <p>{@code images}는 인덱스가 곧 정렬 순서고 0번이 대표. {@code variantPrices}는
 * (요청 반영 후) 살아있는 전 variant 를 빠짐없이 덮어야 한다.
 */
public record ListingUpsertRequest(
        String title,
        String description,
        @JsonProperty("isSinglePieceAllowed") Boolean isSinglePieceAllowed,
        List<String> images,
        List<VariantPriceRequest> variantPrices
) {}
