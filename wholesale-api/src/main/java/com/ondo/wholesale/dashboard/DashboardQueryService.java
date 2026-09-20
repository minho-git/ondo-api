package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 *
 * <p><b>이 메서드에 트랜잭션이 없는 것은 일부러다.</b> 갱신(쓰기)과 읽기는 각자의 트랜잭션에서
 * <b>차례로</b> 돈다. 읽기 트랜잭션으로 감싼 뒤 그 안에서 갱신을 부르면 한 요청이 커넥션을
 * 두 개 쥔다 — 부하 시험에서 요청 10개가 각자 하나씩 쥔 채 서로의 두 번째를 기다려
 * 풀이 통째로 멈췄다(waiting=189, 30초 타임아웃). 순서대로 돌면 한 번에 하나만 쓴다.
 */
@Service
public class DashboardQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DashboardSummaryAssembler assembler;
    private final DashboardSummaryRefresher refresher;
    private final DashboardFreshness freshness;
    private final Duration staleAfter;

    public DashboardQueryService(DashboardSummaryAssembler assembler,
                                 DashboardSummaryRefresher refresher,
                                 DashboardFreshness freshness,
                                 @Value("${ondo.dashboard.stale-after:20s}") Duration staleAfter) {
        this.assembler = assembler;
        this.refresher = refresher;
        this.freshness = freshness;
        this.staleAfter = staleAfter;
    }

    public DashboardSummaryResponse summary(Long wholesalerId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime businessDayStart = BusinessDay.startFor(now);
        LocalDate todayKst = now.atZoneSameInstant(KST).toLocalDate();
        LocalDate businessDay = businessDayStart.atZoneSameInstant(KST).toLocalDate();

        freshness.refreshIfStale(wholesalerId, staleAfter, refresher::refresh);

        return assembler.assemble(wholesalerId, now, businessDayStart, businessDay, todayKst);
    }
}
