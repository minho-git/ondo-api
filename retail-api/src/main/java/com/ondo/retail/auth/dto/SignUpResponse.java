package com.ondo.retail.auth.dto;

import com.ondo.retail.retailer.domain.Retailer;
import java.time.OffsetDateTime;

/**
 * 가입 완료 응답. 세션을 주지 않는다 — 가입은 늘 PENDING 이라 그 세션으로 할 게 없다.
 * 프론트는 이 값으로 완료 화면을 그리고 로그인 화면으로 보낸다.
 *
 * @param retailerId     소매처 id
 * @param approvalStatus 가입 직후는 항상 PENDING 이다
 * @param appliedAt      신청 시각. 승인 대기 화면에 쓴다
 */
public record SignUpResponse(
        Long retailerId,
        String approvalStatus,
        OffsetDateTime appliedAt) {

    public static SignUpResponse from(Retailer retailer) {
        return new SignUpResponse(
                retailer.getId(),
                retailer.getApprovalStatus().name(),
                retailer.getCreatedAt());
    }
}
