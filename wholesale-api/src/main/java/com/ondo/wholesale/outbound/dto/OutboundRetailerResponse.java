package com.ondo.wholesale.outbound.dto;

import java.time.OffsetDateTime;

/**
 * 출고 탭 아코디언 헤더 한 행 = 소매처 하나. 페이징 단위가 소매처라 같은 소매처가
 * 페이지 경계에서 쪼개지지 않는다. 집계는 현재 필터(status·q·기간)를 반영한다.
 */
public record OutboundRetailerResponse(
        Long retailerId,
        String retailerCode,
        String retailerName,
        int outboundCount,
        int totalQty,
        OffsetDateTime lastCreatedAt,
        OffsetDateTime lastShippedAt
) {}
