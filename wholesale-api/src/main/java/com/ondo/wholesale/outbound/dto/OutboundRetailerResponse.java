package com.ondo.wholesale.outbound.dto;

import java.time.OffsetDateTime;

/**
 * 출고 탭 아코디언 헤더 한 행 = 소매처 하나. 페이징 단위가 소매처라 같은 소매처가
 * 페이지 경계에서 쪼개지지 않는다. 집계는 현재 필터(status·q·기간)를 반영한다.
 * 소매처 코드는 계약에서 뺐다 — 소매 시스템 값이라 필요해지면 소매 연동으로 후속한다.
 */
public record OutboundRetailerResponse(
        Long retailerId,
        String retailerName,
        int outboundCount,
        int totalQty,
        OffsetDateTime lastCreatedAt,
        OffsetDateTime lastShippedAt
) {}
