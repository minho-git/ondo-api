package com.ondo.wholesale.common.error;

import java.util.List;

/**
 * 커스텀 예외 베이스. {@link ErrorCode}와 선택적 필드 상세({@code errors})를 담는다.
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient List<ErrorResponse.FieldError> errors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<ErrorResponse.FieldError> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    /** 필드 하나가 형식 규칙을 어긴 400 — 목록 쿼리 검증들이 같은 꼴로 쓴다. */
    public static ApiException validationFailed(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new ErrorResponse.FieldError(field, reason)));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ErrorResponse.FieldError> errors() {
        return errors;
    }
}
