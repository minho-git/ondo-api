package com.ondo.retail.retailer.domain;

/**
 * 가입 승인 상태. retailer.approval_status 의 CHECK 값과 같아야 한다.
 *
 * <p>승인 화면을 MVP 에서 뺐다(결정.md). 그래서 새로 가입하면 PENDING 에 머물고,
 * 개발은 시드 계정을 APPROVED 로 박아서 한다.
 */
public enum ApprovalStatus {

    /** 가입 직후. 로그인은 되지만 대부분의 API 는 403 이다. */
    PENDING,

    /** 운영자가 승인했다. 모든 API 를 쓸 수 있다. */
    APPROVED,

    /** 거절됐다. 재신청은 추후 기능이다. */
    REJECTED;

    public boolean isApproved() {
        return this == APPROVED;
    }
}
