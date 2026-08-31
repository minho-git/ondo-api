package com.ondo.wholesale.common.error;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. {@code code} 문자열은 enum 이름을 그대로 쓴다.
 *
 * <p>티켓(MUL-66) 명시 4종 + 방어용 2종({@code ACCESS_DENIED}, {@code INTERNAL_ERROR}).
 */
public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    NOT_APPROVED(HttpStatus.FORBIDDEN, "승인 대기 중인 계정입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
