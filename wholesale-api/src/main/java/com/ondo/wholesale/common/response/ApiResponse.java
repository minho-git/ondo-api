package com.ondo.wholesale.common.response;

/**
 * 모든 성공 응답을 감싸는 공통 봉투. 단건도 예외 없이 {@code { "data": ... }} 형태로 나간다.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
