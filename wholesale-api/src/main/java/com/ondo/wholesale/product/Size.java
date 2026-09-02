package com.ondo.wholesale.product;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * variant 사이즈. DB CHECK(V1 `variant_size_ck`)의 7값과 일치하며, 선언 순서가 곧
 * 재고표·variant 정렬 순서다 (api-lite 00_공통 §3 — 서버 강제 정렬 축).
 *
 * <p>{@code 2XL}은 자바 식별자가 될 수 없어 상수명만 {@code X2L}이고 JSON 값은 {@code "2XL"}이다.
 */
public enum Size {
    XS("XS"), S("S"), M("M"), L("L"), XL("XL"), X2L("2XL"), FREE("FREE");

    private final String label;

    Size(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
