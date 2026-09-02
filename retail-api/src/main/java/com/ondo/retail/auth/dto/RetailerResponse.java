package com.ondo.retail.auth.dto;

import com.ondo.retail.retailer.domain.ApprovalHistory;
import com.ondo.retail.retailer.domain.ApprovalStatus;
import com.ondo.retail.retailer.domain.Retailer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 로그인 · 내 정보 조회의 응답 본문. 명세상 두 API 가 같은 모양을 쓴다.
 *
 * <p>헤더의 계정명뿐 아니라 <b>승인 대기 화면과 승인 거절 화면</b>도 이 응답으로 그린다.
 * 그래서 상태만 있으면 안 되고 신청 시각과 거절 사유가 같이 있어야 한다.
 *
 * <p>비밀번호 해시는 절대 담지 않는다. 개인정보(대표자명 · 연락처 · 사업자번호)도
 * 이 화면들에서 쓸 일이 없어 뺐다.
 *
 * @param retailerId     소매처 id
 * @param email          로그인 계정
 * @param shopName       상호. 화면에 보이는 이름이다
 * @param approvalStatus PENDING · APPROVED · REJECTED. 규약상 Enum 은 코드만 내린다
 * @param appliedAt      가입 신청 시각. 세 상태 모두 값이 있다
 * @param approvedAt     승인된 시각. 승인 전이거나 거절됐으면 null
 * @param rejection      거절 사유. REJECTED 일 때만 있고 나머지는 null
 */
public record RetailerResponse(
        Long retailerId,
        String email,
        String shopName,
        String approvalStatus,
        OffsetDateTime appliedAt,
        OffsetDateTime approvedAt,
        Rejection rejection) {

    /**
     * 왜 거절됐는지. 승인 거절 화면이 그대로 그린다.
     *
     * @param actor      <b>항상 "운영자" 다.</b> 컬럼에는 운영자 이메일이 들어가는데
     *                   소매처가 알 이유가 없어서 고정 문자열로 바꿔 내린다
     * @param reason     거절 사유
     * @param rejectedAt 거절 시각
     */
    @Schema(name = "Rejection")
    public record Rejection(String reason, String actor, OffsetDateTime rejectedAt) {

        private static final String MASKED_ACTOR = "운영자";

        static Rejection from(ApprovalHistory history) {
            return new Rejection(history.getReason(), MASKED_ACTOR, history.getCreatedAt());
        }
    }

    /**
     * 거절되지 않은 계정. 이력을 읽을 필요가 없다.
     *
     * <p>승인·대기 상태에서 이력을 조회하면 매 요청마다 쓸데없는 쿼리가 하나 더 나간다.
     * {@code /auth/me} 는 화면을 열 때마다 불리는 자리라 그게 쌓인다.
     */
    public static RetailerResponse from(Retailer retailer) {
        return from(retailer, null);
    }

    /** {@code history} 는 REJECTED 일 때만 넘긴다. 아니면 null 이어도 된다. */
    public static RetailerResponse from(Retailer retailer, ApprovalHistory history) {
        return new RetailerResponse(
                retailer.getId(),
                retailer.getEmail(),
                retailer.getShopName(),
                retailer.getApprovalStatus().name(),
                retailer.getCreatedAt(),
                retailer.getApprovedAt(),
                retailer.getApprovalStatus() == ApprovalStatus.REJECTED && history != null
                        ? Rejection.from(history)
                        : null);
    }
}
