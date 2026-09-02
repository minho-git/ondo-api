package com.ondo.wholesale.common.response;

/**
 * "이미 봉투다" 마커. 구현 타입은 {@code ApiResponseBodyAdvice}가 다시 감싸지 않는다.
 *
 * <p>대부분은 {@link ApiResponse}면 되지만, 계약이 {@code data} 옆에 다른 최상위 필드를
 * 요구하는 응답(예: 미송 목록의 {@code data + stats})은 전용 봉투 record 가 이걸 구현한다.
 * 구현 타입은 반드시 {@code data} 필드를 가져야 한다 — OpenAPI 보정도 그걸 보고 건너뛴다.
 */
public interface ResponseEnvelope {
}
