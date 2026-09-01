package com.ondo.retail.common.error;

/** 우리가 의도적으로 던지는 예외. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
