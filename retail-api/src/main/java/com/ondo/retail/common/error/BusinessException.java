package com.ondo.retail.common.error;

/** 우리가 의도적으로 던지는 예외. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 상황에 따라 문구가 달라져야 할 때 쓴다. 예를 들어 수량 상한을 넘었다면
     * 상한이 몇인지 알려주는 게 낫다 — "최대 5장까지 담을 수 있어요".
     *
     * <p>이 문구가 화면에 그대로 나가므로 "실패" 같은 말을 쓰지 않는다.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
