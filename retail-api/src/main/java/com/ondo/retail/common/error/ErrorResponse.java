package com.ondo.retail.common.error;

import java.util.List;

/**
 * 실패 응답. 성공과 달리 data 봉투가 없다 — 성공/실패는 HTTP status 로 가른다.
 *
 * <p>팀 규약(「응답 구조」 5항)을 따른다.
 * <ul>
 *   <li>배열은 절대 null 로 내리지 않는다. 비었으면 {@code []}</li>
 *   <li>객체 필드는 null 을 명시한다. 필드를 생략하지 않는다</li>
 * </ul>
 *
 * <pre>{ "code": "...", "message": "...", "traceId": null, "errors": [] }</pre>
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        List<FieldError> errors) {

    /**
     * 어느 입력칸이 왜 걸렸는지. 필드 단위 검증 에러가 있을 때만 찬다.
     *
     * @param field   JS 경로 표기. 중첩이면 {@code items[2].quantity}
     * @param code    필드 단위 코드. 프론트가 칸마다 분기할 수 있게
     * @param message 그 칸 아래에 그대로 붙는 문구
     */
    public record FieldError(String field, String code, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String traceId) {
        return new ErrorResponse(code.name(), code.message(), traceId, List.of());
    }

    /** 문구를 상황에 맞게 바꿔서 내려야 할 때. {@link ErrorCode} 의 기본 문구를 대신한다. */
    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, traceId, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String traceId, List<FieldError> errors) {
        return new ErrorResponse(code.name(), code.message(), traceId,
                errors == null ? List.of() : errors);
    }
}
