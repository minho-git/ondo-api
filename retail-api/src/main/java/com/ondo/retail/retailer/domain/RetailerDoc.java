package com.ondo.retail.retailer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 증빙 서류. 소매는 사업자등록증 한 종류뿐이다.
 *
 * <p>재제출 이력을 남기려고 별도 테이블로 뒀다. 부분 유니크 인덱스
 * {@code (retailer_id, doc_type) WHERE is_current} 가 걸려 있어서
 * 재제출하면 이전 건을 먼저 false 로 바꿔야 한다.
 */
@Entity
@Table(name = "retailer_doc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetailerDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retailer_id", nullable = false)
    private Long retailerId;

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    /** 저장소가 돌려준 경로. 로컬이면 파일 경로, S3 면 키다. */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static RetailerDoc bizLicense(Long retailerId, String fileUrl) {
        RetailerDoc it = new RetailerDoc();
        it.retailerId = retailerId;
        it.docType = "BIZ_LICENSE";
        it.fileUrl = fileUrl;
        it.current = true;
        it.createdAt = OffsetDateTime.now();
        return it;
    }
}
