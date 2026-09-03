package com.ondo.wholesale.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondo.wholesale.product.domain.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 게시글. 시즌 재개 시 {@code seasonStartedAt}은 재개 시각으로 갱신되고
 * {@code seasonEndedAt}은 {@code null}로 돌아간다.
 */
public record ListingResponse(
        Long id,
        ListingStatus status,
        String title,
        String description,
        @JsonProperty("isSinglePieceAllowed") Boolean isSinglePieceAllowed,
        OffsetDateTime seasonStartedAt,
        OffsetDateTime seasonEndedAt,
        List<ListingImageResponse> images
) {}
