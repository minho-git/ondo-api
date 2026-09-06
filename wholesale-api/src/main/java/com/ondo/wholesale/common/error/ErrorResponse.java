package com.ondo.wholesale.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 공통 에러 응답. 에러는 {@code data} 봉투를 쓰지 않는다(요구 4).
 *
 * <p>{@code errors}는 상세가 있을 때만 채우고, 없으면 <b>빈 배열</b>이다(null 금지).
 */
public record ErrorResponse(String code, String message, List<FieldError> errors, String traceId) {

    public ErrorResponse {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    /**
     * 필드 단위 상세(주로 validation). {@code data}는 화면이 후속 행동에 쓰는 구조화 정보
     * (예: 재고 조정 409의 취소 후보 포장 목록) — 없으면 직렬화에서 빠진다.
     */
    public record FieldError(String field, String reason,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Object data) {

        public FieldError(String field, String reason) {
            this(field, reason, null);
        }
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldError> errors, String traceId) {
        return new ErrorResponse(code.name(), message, errors, traceId);
    }
}
