package com.ondo.wholesale.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 성공 응답을 감싸는 공통 봉투. 단건도 예외 없이 {@code { "data": ... }} 형태로 나간다.
 *
 * <p>{@code meta}는 페이징 있는 목록에만 담고, 그 외에는 필드 자체를 내보내지 않는다
 * (api-lite 00_공통 §1 — 단건·비페이징 목록은 {@code data}만).
 */
public record ApiResponse<T>(T data, @JsonInclude(JsonInclude.Include.NON_NULL) PageMeta meta)
        implements ResponseEnvelope {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    /** 페이징 있는 목록 응답. 컨트롤러가 직접 만들어 반환한다 — 봉투 advice 는 이중으로 감싸지 않는다. */
    public static <T> ApiResponse<T> paged(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta);
    }

    /** 페이징 메타 (api-lite 00_공통 §1·§3 — page 는 0-base). */
    public record PageMeta(int page, int size, long totalElements, int totalPages) {}
}
