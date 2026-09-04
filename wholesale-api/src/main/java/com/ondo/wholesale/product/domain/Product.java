package com.ondo.wholesale.product.domain;

import com.ondo.wholesale.master.domain.Color;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 상품. 색상옵션·variant 를 거느리는 애그리거트 뿌리다 (MUL-89).
 *
 * <p>wholesalerId·categoryId 는 연관 없이 Long 으로만 든다 — 스코핑·필터가 전부 id 비교라
 * 엔티티 참조가 필요 없다. 게시글(listing)도 매핑하지 않는다: 비소유 OneToOne 은 LAZY 가
 * 사실상 안 먹혀 N+1 을 부르므로 ListingRepository 로 따로 조회한다.
 *
 * <p>variant_seq 채번은 이 엔티티의 {@link #nextVariantSeq()} 가 유일한 통로다.
 * 수정·삭제 트랜잭션이 상품 행을 비관적 잠금으로 잡으면 채번도 함께 직렬화된다.
 */
@Entity
@Table(name = "product", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wholesaler_id", nullable = false, updatable = false)
    private Long wholesalerId;

    /** 도매처별 연번. 삭제해도 재사용하지 않는다 — 영구 결번 (D-004). */
    @Column(name = "product_number", nullable = false, updatable = false)
    private int productNumber;

    @Column(nullable = false, length = 100)
    private String name;

    /** 리프(depth 3) 카테고리만 — DB 제약이 아니라 서비스가 강제한다 (D-058). */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "last_variant_seq", nullable = false)
    private int lastVariantSeq;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.PERSIST)
    private List<ColorOption> colorOptions = new ArrayList<>();

    @Builder
    private Product(Long wholesalerId, int productNumber, String name, Long categoryId) {
        this.wholesalerId = wholesalerId;
        this.productNumber = productNumber;
        this.name = name;
        this.categoryId = categoryId;
    }

    /** 색상옵션 한 건을 붙인다. 양쪽 참조를 함께 세운다. */
    public ColorOption addColorOption(Color color) {
        ColorOption option = new ColorOption(this, color);
        colorOptions.add(option);
        return option;
    }

    /** variant_seq 발급. 영구 결번 — 삭제된 variant 의 번호는 다시 쓰지 않는다. */
    public int nextVariantSeq() {
        return ++lastVariantSeq;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeCategory(Long categoryId) {
        this.categoryId = categoryId;
    }

    public List<ColorOption> getColorOptions() {
        return Collections.unmodifiableList(colorOptions);
    }
}
