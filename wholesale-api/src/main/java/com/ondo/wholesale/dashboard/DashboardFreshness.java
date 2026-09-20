package com.ondo.wholesale.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * 요약이 오래됐으면 다시 계산한다 (MUL-135).
 *
 * <p><b>왜 필요한가</b> — 대시보드는 30초마다 자동 갱신된다. 도매처 수백 곳이 화면을 켜두면
 * 만료되는 순간 여러 요청이 동시에 "내가 다시 계산해야겠다"고 판단한다. 그대로 두면 같은
 * 재계산이 한꺼번에 돌아 DB 를 때린다. 그래서 <b>한 번만</b> 돌게 만든다.
 *
 * <p>잠금은 DB 에 맡긴다. 태스크가 여러 개라 애플리케이션 메모리 잠금으로는 못 막는다.
 * {@code pg_try_advisory_xact_lock} 은 못 잡으면 기다리지 않고 바로 false 를 준다 —
 * 그 요청은 재계산을 건너뛰고 <b>조금 오래된 값을 읽는다</b>. 화면이 30초 주기라 그래도 된다.
 * 잠금과 갱신 트랜잭션은 {@link DashboardRefreshRunner} 에 있다.
 */
@Component
public class DashboardFreshness {

    private static final Logger log = LoggerFactory.getLogger(DashboardFreshness.class);

    private final DashboardStaleness staleness;
    private final DashboardRefreshRunner runner;

    public DashboardFreshness(DashboardStaleness staleness, DashboardRefreshRunner runner) {
        this.staleness = staleness;
        this.runner = runner;
    }

    /**
     * 마지막 갱신이 {@code staleAfter} 보다 오래됐으면 {@code refresh} 를 부른다.
     *
     * <p>갱신 중 실패해도 예외를 밖으로 내지 않는다. 대시보드는 숫자가 조금 오래되는 것보다
     * 화면이 안 뜨는 쪽이 나쁘다.
     */
    public void refreshIfStale(long wholesalerId, Duration staleAfter, LongConsumer refresh) {
        if (!staleness.isStale(wholesalerId, staleAfter)) {
            return;
        }
        try {
            runner.refreshOnce(wholesalerId, staleAfter, refresh);
        } catch (RuntimeException e) {
            log.warn("대시보드 요약 갱신 실패. 오래된 값으로 응답한다. wholesalerId={}", wholesalerId, e);
        }
    }
}
