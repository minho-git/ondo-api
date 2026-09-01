package com.ondo.retail.auth.dto;

import com.ondo.retail.retailer.domain.Retailer;
import java.time.OffsetDateTime;

/**
 * 로그인 · 내 정보 조회의 응답 본문. 명세상 두 API 가 같은 모양을 쓴다.
 *
 * <p>비밀번호 해시는 절대 담지 않는다. 개인정보(대표자명 · 연락처 · 사업자번호)도
 * 로그인 화면에서 쓸 일이 없어 뺐다.
 */
public record RetailerResponse(
        Long retailerId,
        String email,
        String shopName,
        String approvalStatus,
        OffsetDateTime approvedAt) {

    public static RetailerResponse from(Retailer retailer) {
        return new RetailerResponse(
                retailer.getId(),
                retailer.getEmail(),
                retailer.getShopName(),
                retailer.getApprovalStatus().name(),   // 규약상 Enum 은 코드만 내린다
                retailer.getApprovedAt());
    }
}
