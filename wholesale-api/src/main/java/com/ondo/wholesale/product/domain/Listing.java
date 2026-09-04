package com.ondo.wholesale.product.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
 * 게시글. 상품당 1건(listing_product_uk, D-079)이고 마켓 노출의 단위다.
 *
 * <p>이미지는 전체 교체 계약이라 orphanRemoval — 목록에서 빠진 행은 DB 에서도 지워진다.
 * 게시 올림/내림(ON_SALE ↔ SEASON_ENDED)은 게시글 통째로만 한다 — 옵션 단위 부분 게시는
 * 하지 않기로 확정했다(2026-09-02). 상태 전이 검증(409)은 서비스 몫이다.
 */
@Entity
@Table(name = "listing", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    /** 검색용 이름. 품명과 별개다. */
    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "single_piece_allowed", nullable = false)
    private boolean singlePieceAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(name = "season_started_at")
    private OffsetDateTime seasonStartedAt;

    @Column(name = "season_ended_at")
    private OffsetDateTime seasonEndedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ListingImage> images = new ArrayList<>();

    @Builder
    private Listing(Product product, String title, String description, boolean singlePieceAllowed) {
        this.product = product;
        this.title = title;
        this.description = description;
        this.singlePieceAllowed = singlePieceAllowed;
        this.status = ListingStatus.ON_SALE;
        this.seasonStartedAt = OffsetDateTime.now();
    }

    /** 이미지 전체 교체. 인덱스가 곧 sortOrder 고 0번이 대표다. */
    public void replaceImages(List<String> urls) {
        images.clear();
        for (int i = 0; i < urls.size(); i++) {
            images.add(new ListingImage(this, urls.get(i), i));
        }
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    /** null 로 지운다 — PATCH 에서 빈 문자열이 "지움"으로 들어온다. */
    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateSinglePieceAllowed(boolean singlePieceAllowed) {
        this.singlePieceAllowed = singlePieceAllowed;
    }

    public List<ListingImage> getImages() {
        return Collections.unmodifiableList(images);
    }
}
