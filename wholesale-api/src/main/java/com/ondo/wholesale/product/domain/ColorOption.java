package com.ondo.wholesale.product.domain;

import com.ondo.wholesale.master.domain.Color;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 상품의 색상옵션. <b>행을 지우지 않는다 (D-053)</b> — deleted_at 컬럼조차 없다.
 * 색을 빼면 밑의 variant 만 soft delete 하고 옵션 행은 남긴다.
 * 살아있는 variant 가 없는 옵션은 응답 조립 단계에서 숨긴다.
 */
@Entity
@Table(name = "color_option", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColorOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false, updatable = false)
    private Color color;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "colorOption", cascade = CascadeType.PERSIST)
    private List<Variant> variants = new ArrayList<>();

    ColorOption(Product product, Color color) {
        this.product = product;
        this.color = color;
    }

    /**
     * variant 한 건을 붙인다. seq 는 {@link Product#nextVariantSeq()} 로 발급받아 넘긴다.
     * soft delete 된 같은 사이즈가 있어도 새 행으로 들어간다 — 부분 유니크(variant_size_uk)가 허용한다.
     */
    public Variant addVariant(Size size, int variantSeq) {
        Variant variant = new Variant(this, product, size, variantSeq);
        variants.add(variant);
        return variant;
    }

    public List<Variant> getVariants() {
        return Collections.unmodifiableList(variants);
    }
}
