package com.ondo.wholesale.outbound.dto;

/**
 * 포장 대기 탭 아코디언 헤더 한 행 = 소매처 하나. 페이징 없음.
 * {@code itemCount}는 행 수 — SKU 종류 수가 아니다(같은 SKU 가 두 주문에서 오면 2건).
 * 집계는 현재 필터(q·receiveBy)를 반영하므로 펼칠 때 같은 값을 넘긴다.
 * 소매처 코드는 계약에서 뺐다 — 소매 시스템 값이라 필요해지면 소매 연동으로 후속한다.
 */
public record PackingRetailerResponse(
        Long retailerId,
        String retailerName,
        int itemCount,
        int totalQty
) {}
