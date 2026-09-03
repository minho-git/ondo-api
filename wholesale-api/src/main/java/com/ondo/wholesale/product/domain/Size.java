package com.ondo.wholesale.product.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final Map<String, Size> BY_LABEL =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(s -> s.label, s -> s));

    /** DB 라벨('2XL' 등) → 상수. autoApply 컨버터가 행마다 부르므로 맵 조회로 한다. */
    public static Size fromLabel(String label) {
        Size size = BY_LABEL.get(label);
        if (size == null) {
            throw new IllegalArgumentException("알 수 없는 사이즈: " + label);
        }
        return size;
    }
}
