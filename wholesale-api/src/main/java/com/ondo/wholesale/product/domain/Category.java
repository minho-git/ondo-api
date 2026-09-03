package com.ondo.wholesale.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 마스터 (common 스키마, 읽기 전용).
 *
 * <p>트리는 depth 1~3 고정이고 행이 소량이라 전건 로드 후 메모리에서 조립한다 —
 * self 참조 연관 대신 parentId 를 Long 으로만 든다. 값 변경은 마이그레이션(시드)으로만 한다.
 */
@Entity
@Table(name = "category", schema = "common")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    private Long id;

    /** depth 1 은 null. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    /** 1~3 — DB category_depth_ck 가 강제한다. */
    @Column(nullable = false)
    private short depth;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
