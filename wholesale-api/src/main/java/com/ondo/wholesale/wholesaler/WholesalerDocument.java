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
 * 증빙 서류. 재신청 때 행을 추가하며, type 별 최신 행이 현재 서류다.
 *
 * <p>{@code fileKey} 에는 <b>저장소 키</b>가 들어간다 — {@code uploads/2026/09/ab12.jpg}.
 * 절대 URL 을 넣지 않는다. 도메인·버킷이 바뀌어도 쌓인 행을 고칠 필요가 없고,
 * 사업자등록증·신분증처럼 개인정보인 파일을 비공개 버킷 + 서명 URL 로 옮길 때
 * 설정만 바꾸면 되기 때문이다.
 *
 * <p>파일을 실제로 올리는 경로(presigned 발급)는 아직 없다. 지금은 키 형식만 본다.
 * TODO(MUL-45): 발급 API 가 서버에서 키를 채번해 신청에 묶으면, 남의 키를
 * 가리키는 경우까지 막힌다.
 */
@Entity
@Table(name = "wholesaler_document", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WholesalerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wholesaler_id", nullable = false)
    private Wholesaler wholesaler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    WholesalerDocument(Wholesaler wholesaler, DocumentType type, String fileKey) {
        this.wholesaler = wholesaler;
        this.type = type;
        this.fileKey = fileKey;
    }
}
