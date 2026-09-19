package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 대시보드 summary 조립 (MUL-120) — 로그인한 도매처 기준, 파라미터 없음.
 *
 * <p>{@code now}는 한 번만 구해 응답 필드·영업일 경계·입고일 판정에 같은 값을 쓴다 —
 * 세 군데가 다른 시각을 보면 경계 근처에서 숫자가 서로 어긋난다.
 *
 * <p>무거운 집계는 요약 표에서 읽는다(MUL-135). 요약이 오래됐으면 이 도매처 것만 다시
 * 계산하고 읽는다. 화면이 30초마다 갱신되므로 갱신 주기를 그보다 짧게 둘 이유가 없다.
 */
@Service
public class DashboardQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DashboardSummaryReader reader;
    private final DashboardSummaryRefresher refresher;
    private final DashboardFreshness freshness;
    private final Duration staleAfter;

    public DashboardQueryService(DashboardSummaryReader reader,
                                 DashboardSummaryRefresher refresher,
                                 DashboardFreshness freshness,
                                 @Value("${ondo.dashboard.stale-after:20s}") Duration staleAfter) {
        this.reader = reader;
        this.refresher = refresher;
        this.freshness = freshness;
        this.staleAfter = staleAfter;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long wholesalerId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime businessDayStart = BusinessDay.startFor(now);
        LocalDate todayKst = now.atZoneSameInstant(KST).toLocalDate();
        LocalDate businessDay = businessDayStart.atZoneSameInstant(KST).toLocalDate();

        freshness.refreshIfStale(wholesalerId, staleAfter, refresher::refresh);

        var newOrders = reader.newOrders(wholesalerId);
        var packing = reader.packing(wholesalerId);
        var outbound = reader.outbound(wholesalerId, businessDayStart);
        var backorder = reader.backorder(wholesalerId, todayKst);
        var todayOrders = reader.todayOrders(wholesalerId, businessDay);
        var todayShipped = reader.todayShipped(wholesalerId, businessDayStart);

        return new DashboardSummaryResponse(
                now,
                new DashboardSummaryResponse.NewOrders(
                        newOrders.count(), newOrders.oldestOrderedAt(), newOrders.oldestRetailerName()),
                new DashboardSummaryResponse.Packing(
                        packing.retailerCount(), packing.qty(), packing.byReceive()),
                new DashboardSummaryResponse.Outbound(
                        outbound.notShippedCount(), outbound.staleCount()),
                new DashboardSummaryResponse.Backorder(
                        backorder.skuCount(), backorder.qty(),
                        backorder.overdueSkuCount(), backorder.noDateSkuCount()),
                new DashboardSummaryResponse.Today(
                        new DashboardSummaryResponse.Today.Orders(
                                todayOrders.count(), todayOrders.amount()),
                        todayOrders.cancelled(),
                        new DashboardSummaryResponse.Today.Shipped(
                                todayShipped.count(), todayShipped.qty())));
    }
}
