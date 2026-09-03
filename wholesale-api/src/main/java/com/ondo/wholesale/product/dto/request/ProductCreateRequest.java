package com.ondo.wholesale.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 상품 등록 요청 (api-lite/02_상품게시/POST_products.md).
 *
 * <p>{@code categoryId}는 리프(depth 3)만 허용. {@code listing: null}은
 * "게시글 없이 상품만 등록"이라는 정상 값이다.
 *
 * <p>형식(비어 있음·길이)은 여기 Bean Validation → {@code VALIDATION_FAILED},
 * 정책(옵션 구성·카테고리)은 서비스 → 전용 코드. 빈 {@code colorOptions}는 형식이 아니라
 * 정책({@code OPTION_REQUIRED})이라 여기서 막지 않는다.
 */
public record ProductCreateRequest(
        @Schema(example = "오버핏 코튼 티셔츠")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(example = "121", description = "소분류(리프) 카테고리 id")
        @NotNull
        Long categoryId,

        @Valid
        List<ColorOptionRequest> colorOptions,

        @Valid
        ListingUpsertRequest listing
) {
    public ProductCreateRequest {
        colorOptions = (colorOptions == null) ? List.of() : colorOptions;
    }
}
