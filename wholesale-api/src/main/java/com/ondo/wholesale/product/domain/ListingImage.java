package com.ondo.wholesale.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 게시글 이미지. 전체 교체로만 바뀐다 — 개별 수정이 없어 updated_at 컬럼 자체가 없다.
 * 행 생성·삭제는 {@link Listing#replaceImages} 가 orphanRemoval 로 관리한다.
 */
@Entity
@Table(name = "listing_image", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    private Listing listing;

    @Column(nullable = false, length = 500)
    private String url;

    /** 0 = 대표 이미지. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    ListingImage(Listing listing, String url, int sortOrder) {
        this.listing = listing;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}
