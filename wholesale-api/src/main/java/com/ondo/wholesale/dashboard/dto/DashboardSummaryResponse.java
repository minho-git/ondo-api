package com.ondo.wholesale.dashboard.dto;

import com.ondo.wholesale.order.ReceiveBy;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 대시보드 summary 한 방 응답 (MUL-120) — 30초 polling 으로 호출되므로
 * 1차 화면의 숫자만 담고 2차 항목(어제 대비·미수·추이)은 넣지 않는다.
 *
 * <p>{@code now}는 서버 시각 — FE 시계로 경과 시간을 재면 클라이언트 시계 오차가
 * 섞이므로 서버가 준다. "오늘" 집계의 경계는 자정이 아니라 영업일 시작
 * ({@link com.ondo.wholesale.dashboard.BusinessDay})이다.
 */
public record DashboardSummaryResponse(
        OffsetDateTime now,
        NewOrders newOrders,
        Packing packing,
        Outbound outbound,
        Backorder backorder,
        Today today
) {

    /** 확정 기다리는 주문 타일 — 가장 오래 기다린 주문의 접수 시각과 소매처명 포함. */
    public record NewOrders(int count, OffsetDateTime oldestOrderedAt, String oldestRetailerName) {}

    /** 포장 대기 타일 — byReceive 는 수령방식별 소매처 수 ({@code {"AGENT": 2, "RETAILER": 1}}). */
    public record Packing(int retailerCount, int qty, Map<ReceiveBy, Integer> byReceive) {}

    /** 출고 확정 안 한 봉투 타일 — staleCount 는 이번 영업일 시작 전에 포장한 봉투 수. */
    public record Outbound(int notShippedCount, int staleCount) {}

    /** 미송 타일 — 입고일 지남/미등록 SKU 수 포함. */
    public record Backorder(int skuCount, int qty, int overdueSkuCount, int noDateSkuCount) {}

    /** 오늘(영업일) 영역 — 주문·취소·출고. cancelled 는 오늘 접수분 중 취소 건수. */
    public record Today(Orders orders, int cancelled, Shipped shipped) {

        public record Orders(int count, int amount) {}

        public record Shipped(int count, int qty) {}
    }
}
