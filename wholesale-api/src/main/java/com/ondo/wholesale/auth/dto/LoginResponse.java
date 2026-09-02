package com.ondo.wholesale.auth.dto;

import com.ondo.wholesale.security.ApprovalStatus;

/**
 * 로그인 응답 (MUL-69).
 *
 * <p>승인 상태 하나뿐이다. <b>토큰 필드가 없다</b> — 세션 방식이라 인증은 쿠키로 오간다.
 *
 * <p>컨트롤러가 이 record 를 <b>그대로</b> 반환하면 {@code ApiResponseBodyAdvice} 가
 * {@code {"data":{...}}} 봉투를 씌운다. {@code ApiResponse.of()} 로 직접 감싸면 봉투가 두 겹이 된다.
 */
public record LoginResponse(ApprovalStatus approvalStatus) {
}
