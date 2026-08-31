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

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ErrorResponse.FieldError> errors() {
        return errors;
    }
}
