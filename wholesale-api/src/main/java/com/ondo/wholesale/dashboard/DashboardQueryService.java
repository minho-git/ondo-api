package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 대시보드 summary 조립 (MUL-120) — 로그인한 도매처 기준, 파라미터 없음.
 *
 * <p>{@code now}는 한 번만 구해 응답 필드·영업일 경계·입고일 판정에 같은 값을 쓴다 —
 * 세 군데가 다른 시각을 보면 경계 근처에서 숫자가 서로 어긋난다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DashboardSummaryReader reader;

    public DashboardQueryService(DashboardSummaryReader reader) {
        this.reader = reader;
    }

    public DashboardSummaryResponse summary(Long wholesalerId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime businessDayStart = BusinessDay.startFor(now);
        LocalDate todayKst = now.atZoneSameInstant(KST).toLocalDate();

        var newOrders = reader.newOrders(wholesalerId);
        var packing = reader.packing(wholesalerId);
        var outbound = reader.outbound(wholesalerId, businessDayStart);
        var backorder = reader.backorder(wholesalerId, todayKst);
        var todayOrders = reader.todayOrders(wholesalerId, businessDayStart);
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
