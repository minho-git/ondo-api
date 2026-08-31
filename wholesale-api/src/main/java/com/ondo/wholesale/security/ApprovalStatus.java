package com.ondo.wholesale.security;

/**
 * 도매처 승인 상태. 값 문자열은 DB CHECK 제약(wholesaler.approval_status)과 1:1로 맞춘다.
 *
 * <p>회원 엔티티(MUL-67)가 {@code @Enumerated(STRING)}으로 이 enum을 재사용한다(중복 정의 금지).
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
