package com.ondo.retail.common.response;

/**
 * 성공 응답 봉투. 팀 규약상 성공은 항상 data 로 감싼다.
 *
 * <pre>{ "data": { ... } }</pre>
 *
 * @param data 실제 내용. 성공은 항상 이 안에 들어온다
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
