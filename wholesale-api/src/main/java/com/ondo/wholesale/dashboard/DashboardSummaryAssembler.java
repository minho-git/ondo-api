package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 요약 여섯 개를 한 스냅숏에서 읽어 응답으로 조립한다 (MUL-120 · MUL-135).
 *
 * <p>한 트랜잭션으로 묶는 이유 — 여섯 값이 서로 다른 시점을 보면 화면의 숫자끼리 어긋난다.
 * 여기서 <b>읽기만</b> 한다. 갱신은 {@link DashboardQueryService} 가 이 트랜잭션에 들어오기
 * 전에 끝낸다 — 읽기 트랜잭션 안에서 갱신을 부르면 커넥션을 두 개 쥐게 되고, 부하가 걸리면
 * 서로의 두 번째 커넥션을 기다리다 풀이 통째로 멈춘다.
 */
@Component
public class DashboardSummaryAssembler {

    private final DashboardSummaryReader reader;

    public DashboardSummaryAssembler(DashboardSummaryReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse assemble(long wholesalerId, OffsetDateTime now,
                                             OffsetDateTime businessDayStart,
                                             LocalDate businessDay, LocalDate todayKst) {
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
