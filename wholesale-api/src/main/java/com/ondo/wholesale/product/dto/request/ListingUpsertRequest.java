package com.ondo.wholesale.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 게시글 본문. 등록(POST)에선 생성, 수정(PATCH)에선 upsert 로 쓰인다.
 *
 * <p>{@code images}는 인덱스가 곧 정렬 순서고 0번이 대표. {@code variantPrices}는
 * (요청 반영 후) 살아있는 전 variant 를 빠짐없이 덮어야 한다.
 *
 * <p>{@code title}에 {@code @NotBlank}를 걸지 않는 이유 — PATCH 는 "생략 = 무변경"이라
 * 제목 없이 올 수 있다. 생성 분기에서 제목이 필요한 건 서비스가 검사한다.
 */
public record ListingUpsertRequest(
        @Schema(example = "[신상] 오버핏 코튼 티셔츠 데일리 남방")
        @Size(max = 100)
        String title,

        @Schema(example = "넉넉한 오버핏 실루엣의 데일리 셔츠예요.")
        String description,

        @Schema(example = "true", description = "낱장 판매 허용")
        @JsonProperty("isSinglePieceAllowed") Boolean isSinglePieceAllowed,

        @Schema(description = "인덱스가 곧 정렬 순서, 0번이 대표")
        List<String> images,

        @Valid
        List<VariantPriceRequest> variantPrices
) {
    // 정규화하지 않는다 — PATCH 에서 "생략(null) = 무변경"과 "빈 배열 = 전부 삭제"를 구분해야 한다.
}
