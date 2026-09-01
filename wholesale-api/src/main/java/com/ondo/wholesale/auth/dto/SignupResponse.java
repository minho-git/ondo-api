package com.ondo.wholesale.auth.dto;

import com.ondo.wholesale.security.ApprovalStatus;

import java.time.OffsetDateTime;

/**
 * 가입 신청 결과. 201 로 나간다.
 *
 * <p>세션을 주지 않는다 — 가입 직후는 언제나 PENDING 이고, 다시 들어올 때는
 * 로그인 → 상태 조회 경로를 탄다.
 *
 * @param appliedAt 신청 일시. approval_request 행의 created_at 이다
 *                  (wholesaler 의 것이 아니다 — 재신청하면 라운드마다 갱신돼야 한다)
 */
public record SignupResponse(
        ApprovalStatus approvalStatus,
        String bizName,
        String bizRegNo,
        OffsetDateTime appliedAt) {
}
