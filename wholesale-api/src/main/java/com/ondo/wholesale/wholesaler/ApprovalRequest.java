package com.ondo.wholesale.wholesaler;

import com.ondo.wholesale.security.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 심사 신청 한 라운드. 최초 신청과 재신청마다 행이 하나씩 생긴다(MUL-67).
 *
 * <p>화면의 "신청 일시"가 이 행의 {@code createdAt} 이다. 예전처럼 wholesaler 컬럼을
 * 덮어쓰면 재신청할 때 이전 라운드의 거절 사유가 사라진다.
 *
 * <p>도매처와 {@code @ManyToOne} 으로 묶지 않고 id 만 들고 있다. 이 행은 도매처에
 * 딸린 부품이 아니라 <b>재신청 때 혼자 늘어나는 이력</b>이라, 독립 리포지토리로 다루는 게 맞다.
 *
 * <p><b>심사 결과 컬럼은 아직 매핑하지 않았다</b>(reason · document_types · actor · decided_at).
 * 승인·거절을 수행하는 쪽은 운영자 내부 도구라 이 프로젝트 범위 밖이고,
 * 거절 사유를 읽는 화면은 MUL-70·71 이다. 그때 추가한다.
 */
@Entity
@Table(name = "approval_request", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wholesaler_id", nullable = false)
    private Long wholesalerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** 화면의 "신청 일시". */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private ApprovalRequest(Long wholesalerId) {
        this.wholesalerId = wholesalerId;
    }

    /**
     * 심사 대기 라운드를 연다.
     *
     * <p>한 도매처에 PENDING 은 하나뿐이다 — approval_request_pending_uk 가 막는다.
     * 재신청 연타가 라운드를 둘로 벌리지 못한다.
     */
    public static ApprovalRequest pending(Long wholesalerId) {
        return new ApprovalRequest(wholesalerId);
    }
}
