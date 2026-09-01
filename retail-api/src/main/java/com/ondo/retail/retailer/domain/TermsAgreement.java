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

/** 약관 동의 이력. 동의한 종류마다 한 줄씩 쌓인다. */
@Entity
@Table(name = "terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retailer_id", nullable = false)
    private Long retailerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 30)
    private TermsType termsType;

    @Column(name = "agreed_at", nullable = false)
    private OffsetDateTime agreedAt;

    public static TermsAgreement of(Long retailerId, TermsType type) {
        TermsAgreement it = new TermsAgreement();
        it.retailerId = retailerId;
        it.termsType = type;
        it.agreedAt = OffsetDateTime.now();
        return it;
    }
}
