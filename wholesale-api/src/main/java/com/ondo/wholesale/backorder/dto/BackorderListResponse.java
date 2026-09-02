package com.ondo.wholesale.backorder.dto;

import com.ondo.wholesale.common.response.ResponseEnvelope;

import java.util.List;

/**
 * SKU별 미송 목록 봉투 (api-lite/05_미송/GET_variants_{variantId}_backorders.md) —
 * 계약이 {@code meta} 대신 {@code data + stats}를 요구해 전용 봉투를 쓴다.
 * 페이징 없음: 화면이 배분 입력칸을 전부 그린 뒤 한 번에 확정하는 구조라서다.
 */
public record BackorderListResponse(List<BackorderResponse> data, BackorderStatsResponse stats)
        implements ResponseEnvelope {}
