package com.ondo.wholesale.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 색상 마스터 (common 스키마, 읽기 전용).
 *
 * <p>응답에 색 이름·hex·그룹명이 함께 나가므로 그룹을 연관으로 든다.
 * 조회 서비스는 fetch join 으로 가져온다 — LAZY 프록시를 뷰까지 끌고 가지 않는다.
 */
@Entity
@Table(name = "color", schema = "common")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Color {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ColorGroup group;

    @Column(nullable = false, length = 30)
    private String name;

    /** #RRGGBB. 없을 수 있다(패턴 프린트 등). DB 는 char(7) — validate 가 CHAR 타입까지 본다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String hex;

    /** 그룹 내 순서 — 팔레트 정렬의 2축. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
