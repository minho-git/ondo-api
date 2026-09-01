package com.ondo.retail.common.response;

/**
 * 성공 응답 봉투. 팀 규약상 성공은 항상 data 로 감싼다.
 *
 * <pre>{ "data": { ... } }</pre>
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
