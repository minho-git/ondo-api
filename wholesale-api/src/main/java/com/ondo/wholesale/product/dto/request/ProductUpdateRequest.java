package com.ondo.wholesale.product.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;
import java.util.Optional;

/**
 * 상품 수정 요청 (api-lite/02_상품게시/PATCH_products.md).
 *
 * <p>PATCH 의미론 — <b>생략 = 무변경</b>, <b>명시적 null = 400</b>. 이 둘을 구분해야 해서
 * record 가 아니라 세터 바인딩 클래스다: 생략된 필드는 세터가 안 불려 {@code null}로 남고,
 * 명시적 null 은 Jackson 이 {@code Optional.empty()}로 넣는다. (record 생성자 바인딩은
 * 생략도 empty 로 만들어 구분이 사라진다.)
 *
 * <p>{@code colorOptions}·{@code listing.images}·{@code listing.variantPrices}는
 * 보낸 경우 전체 교체다. listing 내부 스칼라는 null = 무변경 (팀 결정 2026-09-04) —
 * description 을 지우려면 빈 문자열을 보낸다.
 */
public class ProductUpdateRequest {

    @Schema(example = "오버핏 코튼 티셔츠")
    private Optional<String> name;

    @Schema(example = "121")
    private Optional<Long> categoryId;

    private Optional<List<@Valid ColorOptionRequest>> colorOptions;

    private Optional<@Valid ListingUpsertRequest> listing;

    public Optional<String> name() {
        return name;
    }

    public Optional<Long> categoryId() {
        return categoryId;
    }

    public Optional<List<ColorOptionRequest>> colorOptions() {
        return colorOptions;
    }

    public Optional<ListingUpsertRequest> listing() {
        return listing;
    }

    public void setName(Optional<String> name) {
        this.name = name;
    }

    public void setCategoryId(Optional<Long> categoryId) {
        this.categoryId = categoryId;
    }

    public void setColorOptions(Optional<List<ColorOptionRequest>> colorOptions) {
        this.colorOptions = colorOptions;
    }

    public void setListing(Optional<ListingUpsertRequest> listing) {
        this.listing = listing;
    }

    @JsonIgnore
    @AssertTrue(message = "명시적 null 은 허용하지 않는다 — 바꾸지 않을 필드는 생략한다.")
    @Schema(hidden = true)
    public boolean isNameNotExplicitNull() {
        return name == null || name.isPresent();
    }

    @JsonIgnore
    @AssertTrue(message = "명시적 null 은 허용하지 않는다 — 바꾸지 않을 필드는 생략한다.")
    @Schema(hidden = true)
    public boolean isCategoryIdNotExplicitNull() {
        return categoryId == null || categoryId.isPresent();
    }

    @JsonIgnore
    @AssertTrue(message = "명시적 null 은 허용하지 않는다 — 바꾸지 않을 필드는 생략한다.")
    @Schema(hidden = true)
    public boolean isColorOptionsNotExplicitNull() {
        return colorOptions == null || colorOptions.isPresent();
    }

    @JsonIgnore
    @AssertTrue(message = "명시적 null 은 허용하지 않는다 — 게시글만 지우는 길은 없다(상품 삭제뿐).")
    @Schema(hidden = true)
    public boolean isListingNotExplicitNull() {
        return listing == null || listing.isPresent();
    }

    @JsonIgnore
    @AssertTrue(message = "이름은 비울 수 없고 100자 이내다.")
    @Schema(hidden = true)
    public boolean isNameWellFormed() {
        if (name == null || name.isEmpty()) {
            return true; // 생략/명시적 null 은 위의 규칙이 다룬다
        }
        String value = name.get();
        return !value.isBlank() && value.length() <= 100;
    }
}
