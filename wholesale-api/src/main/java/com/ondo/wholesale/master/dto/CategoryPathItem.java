package com.ondo.wholesale.master.dto;

/** 카테고리 경로 한 칸. {@code categoryPath}는 루트 → 리프, 항상 3단이다. */
public record CategoryPathItem(Long id, String name) {}
