package com.ondo.retail.retailer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인정보. {@link Retailer} 와 1:1 이고 PK 를 공유한다.
 *
 * <p>테이블을 나눈 건 암호화와 다른 목적이다 — SELECT * 에 개인정보가 딸려오지 않게 하려는 것.
 * 탈퇴할 때도 이 행 하나만 지우면 주문·정산 기록은 남는다.
 *
 * <p>⚠️ 지금은 평문이다. AES 는 실서비스 개시 전에 붙인다(결정.md). 그때까지 진짜 개인정보를 넣지 않는다.
 */
@Entity
@Table(name = "retailer_private")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetailerPrivate {

    @Id
    @Column(name = "retailer_id")
    private Long retailerId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Column(nullable = false, length = 255)
    private String mobile;

    /**
     * 사업자등록번호 10자리.
     *
     * <p>중복을 DB 로 막지 않는다. 나중에 AES 를 붙이면 같은 번호도 매번 다른 암호문이 돼서
     * 유니크가 무의미해진다. 지금 걸어두면 그때 풀어야 한다. 운영자가 승인할 때 눈으로 거른다.
     */
    @Column(name = "biz_reg_no", nullable = false, length = 255)
    private String bizRegNo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static RetailerPrivate of(Long retailerId, String ownerName, String mobile, String bizRegNo) {
        RetailerPrivate it = new RetailerPrivate();
        it.retailerId = retailerId;
        it.ownerName = ownerName;
        it.mobile = mobile;
        it.bizRegNo = bizRegNo;
        it.createdAt = OffsetDateTime.now();
        it.updatedAt = it.createdAt;
        return it;
    }
}
