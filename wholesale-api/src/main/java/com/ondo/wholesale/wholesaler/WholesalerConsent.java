package com.ondo.wholesale.wholesaler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 동의 원장. <b>추가만 하고 고치지 않는다.</b>
 *
 * <p>철회는 기존 행을 false 로 바꾸는 게 아니라 {@code agreed = false} 행을 새로 넣는다.
 * 현재 상태는 type 별 최신 행이다. 언제 동의했고 언제 철회했는지가 남아야
 * 나중에 "그때 동의받았느냐"에 답할 수 있다.
 */
@Entity
@Table(name = "wholesaler_consent", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WholesalerConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wholesaler_id", nullable = false)
    private Wholesaler wholesaler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentType type;

    /** 미동의도 false 로 기록한다. "안 물어봤다"와 구분하기 위해서다. */
    @Column(nullable = false)
    private boolean agreed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    WholesalerConsent(Wholesaler wholesaler, ConsentType type, boolean agreed) {
        this.wholesaler = wholesaler;
        this.type = type;
        this.agreed = agreed;
    }
}
