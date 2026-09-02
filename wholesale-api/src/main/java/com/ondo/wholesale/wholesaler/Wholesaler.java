package com.ondo.wholesale.wholesaler;

import com.ondo.wholesale.security.ApprovalStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 도매처 계정. 가입 신청(MUL-68)이 만드는 뿌리 엔티티다.
 *
 * <p><b>컬럼을 다 매핑하지 않았다.</b> {@code ddl-auto: validate} 는 엔티티에 있는 것이
 * DB 에도 있는지만 보고, DB 에만 있는 컬럼은 문제 삼지 않는다. 그래서 가입 단계에서
 * 쓰지 않는 컬럼(bank_*, last_*_seq, approved_at, deleted_at)은 비워뒀다.
 * 필요해지는 티켓에서 그때 추가한다.
 *
 * <p>동의와 서류는 도매처에 딸린 것이라 이 엔티티가 함께 들고 저장한다.
 * 반면 심사 신청(approval_request)은 재신청 때 행만 따로 늘어나므로
 * {@link ApprovalRequest} 를 독립 리포지토리로 다룬다.
 */
@Entity
@Table(name = "wholesaler", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wholesaler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 계정. 항상 소문자로 저장한다 — DB 의 wholesaler_email_lower_ck 가 강제한다. */
    @Column(nullable = false, length = 100)
    private String email;

    /** BCrypt 해시. 평문은 절대 들어오지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    /** 하이픈 없는 숫자 10자리. */
    @Column(name = "biz_reg_no", nullable = false, length = 20)
    private String bizRegNo;

    /** 상호. 소매 화면에 그대로 노출된다. */
    @Column(name = "biz_name", nullable = false, length = 50)
    private String bizName;

    @Column(name = "biz_owner_name", nullable = false, length = 50)
    private String bizOwnerName;

    @Column(name = "store_phone", length = 20)
    private String storePhone;

    /** 누죤·APM 같은 상가 건물명. */
    @Column(name = "store_building", length = 50)
    private String storeBuilding;

    /** "3층 C-25" 같은 호수. */
    @Column(name = "store_unit", length = 50)
    private String storeUnit;

    /** 사업자등록증의 종목 표기 그대로. 화면의 "주요 취급 카테고리"와는 다른 값이다. */
    @Column(name = "biz_category", length = 50)
    private String bizCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "wholesaler", cascade = CascadeType.PERSIST)
    private List<WholesalerConsent> consents = new ArrayList<>();

    @OneToMany(mappedBy = "wholesaler", cascade = CascadeType.PERSIST)
    private List<WholesalerDocument> documents = new ArrayList<>();

    @Builder
    private Wholesaler(String email, String passwordHash, String phone, String bizRegNo,
                       String bizName, String bizOwnerName, String storePhone,
                       String storeBuilding, String storeUnit, String bizCategory) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.bizRegNo = bizRegNo;
        this.bizName = bizName;
        this.bizOwnerName = bizOwnerName;
        this.storePhone = storePhone;
        this.storeBuilding = storeBuilding;
        this.storeUnit = storeUnit;
        this.bizCategory = bizCategory;
    }

    /**
     * 심사 승인. 지금은 로컬 시드({@code LocalDevAccountSeeder})만 쓰지만,
     * 심사 승인 처리(MUL-70/71)가 들어오면 같은 메서드를 탄다.
     * approved_at 컬럼은 아직 미매핑이라 그 티켓에서 함께 확장한다.
     */
    public void approve() {
        this.approvalStatus = ApprovalStatus.APPROVED;
    }

    /**
     * 동의 한 건을 붙인다. 양쪽 참조를 함께 세운다 —
     * 자식의 wholesaler 가 비어 있으면 wholesaler_id 가 NULL 로 나가 INSERT 가 깨진다.
     */
    public void addConsent(ConsentType type, boolean agreed) {
        consents.add(new WholesalerConsent(this, type, agreed));
    }

    /** 서류 한 건을 붙인다. fileKey 는 저장소 키다(절대 URL 이 아니다). */
    public void addDocument(DocumentType type, String fileKey) {
        documents.add(new WholesalerDocument(this, type, fileKey));
    }

    public List<WholesalerConsent> getConsents() {
        return Collections.unmodifiableList(consents);
    }

    public List<WholesalerDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }
}
