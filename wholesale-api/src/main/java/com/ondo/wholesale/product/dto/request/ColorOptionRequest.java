package com.ondo.wholesale.product.dto.request;

import com.ondo.wholesale.product.domain.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 색상 하나의 옵션 구성. {@code sizes}는 1개 이상, 색상 내 중복 불가 — 위반은 정책 코드로 나간다. */
public record ColorOptionRequest(
        @Schema(example = "1", description = "팔레트(GET /colors)의 색상 id")
        @NotNull
        Long colorId,

        @Schema(example = "[\"S\", \"M\"]")
        List<Size> sizes
) {
    public ColorOptionRequest {
        sizes = (sizes == null) ? List.of() : sizes;
    }
}
