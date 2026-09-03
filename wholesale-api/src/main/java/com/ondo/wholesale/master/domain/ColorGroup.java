package com.ondo.wholesale.master.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 색상 그룹 마스터 (common 스키마, 읽기 전용). 팔레트 정렬의 1축이다. */
@Entity
@Table(name = "color_group", schema = "common")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColorGroup {

    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
