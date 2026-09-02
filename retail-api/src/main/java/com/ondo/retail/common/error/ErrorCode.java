package com.ondo.retail.common.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드와 화면 문구를 한곳에서 관리한다.
 *
 * <p>여기 적은 message 가 화면에 그대로 나간다. 그래서 "실패" 라고 쓰지 않는다 —
 * 사용자가 뭘 잘못했을 때 쓰는 말인데 대부분 그런 상황이 아니다.
 * 항상 다음에 뭘 하면 되는지가 읽히게 쓴다.
 */
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근할 수 없어요"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요"),

    // 인증 · 가입
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요"),
    ACCOUNT_NOT_APPROVED(HttpStatus.FORBIDDEN, "승인 후 이용할 수 있어요"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일은 10MB까지 올릴 수 있어요"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "jpg, png, pdf 파일만 올릴 수 있어요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
