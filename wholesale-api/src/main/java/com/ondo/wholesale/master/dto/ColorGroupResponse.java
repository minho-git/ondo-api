package com.ondo.wholesale.master.dto;

import java.util.List;

/**
 * 색상 팔레트의 그룹 (api-lite/02_상품게시/GET_colors.md). 고정 시드라 프론트 캐시 가능.
 * 그룹·그룹 내 색상 순서가 곧 화면 순서다.
 */
public record ColorGroupResponse(Long id, String name, List<ColorItem> colors) {

    /** 팔레트의 색상 하나. {@code id}가 상품 등록·수정 요청의 {@code colorId}다. */
    public record ColorItem(Long id, String name, String hex) {}
}
