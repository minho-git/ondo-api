package com.ondo.retail.retailer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소매 계정. 로그인에 쓰는 정보만 여기 있고, 개인정보는 {@link RetailerPrivate} 로 뺐다.
 *
 * <p>이메일은 {@code lower(email)} 로 유니크라 대소문자를 구분하지 않는다.
 * BomBom@naver.com 과 bombom@naver.com 은 같은 계정이다.
 */
@Entity
@Table(name = "retailer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 가 요구한다. 밖에서는 못 부른다
public class Retailer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // bigserial
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    /** BCrypt 해시. 평문을 넣지 않는다. */
    @Column(nullable = false, length = 255)
    private String password;

    /** 상호. 도매 거래처 목록에 그대로 노출된다. */
    @Column(name = "shop_name", nullable = false, length = 50)
    private String shopName;

    @Enumerated(EnumType.STRING)                          // 숫자가 아니라 문자열로 저장
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    /** 최초 승인 시각. 재심사로 다시 PENDING 이 됐을 때 구분하는 근거다. */
    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 가입 신청. 항상 PENDING 으로 시작한다 — 운영자가 승인해야 쓸 수 있다.
     *
     * @param encodedPassword BCrypt 해시. 평문을 넘기면 안 된다
     */
    public static Retailer signUp(String email, String encodedPassword, String shopName) {
        Retailer it = new Retailer();
        it.email = email;
        it.password = encodedPassword;
        it.shopName = shopName;
        it.approvalStatus = ApprovalStatus.PENDING;
        it.createdAt = OffsetDateTime.now();
        it.updatedAt = it.createdAt;
        return it;
    }

    public boolean isApproved() {
        return approvalStatus.isApproved();
    }
}
