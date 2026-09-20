package com.ondo.wholesale.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * 잠금을 잡은 요청 하나만 재계산을 돌린다 (MUL-135).
 *
 * <p><b>왜 빈이 따로인가</b> — {@code REQUIRES_NEW} 는 스프링 프록시를 거쳐야 걸린다.
 * {@link DashboardFreshness} 안에 두고 자기 메서드를 부르면 프록시를 안 타서 애노테이션이
 * 그냥 무시되고, 조회 쪽의 읽기 전용 트랜잭션 안에서 DELETE 가 돌아 통째로 500 이 된다.
 * 부하 시험에서 잡힌 실제 증상이다.
 *
 * <p>갱신을 읽기 트랜잭션 밖에서 따로 커밋해야 하는 이유는 둘이다 — 조회는 읽기 전용이고,
 * 갱신이 실패해도 화면은 오래된 값으로 떠야 한다.
 */
@Component
public class DashboardRefreshRunner {

    /** advisory lock 의 첫 번째 키 — 다른 기능이 쓰는 잠금과 겹치지 않게 대시보드 전용 값을 둔다. */
    private static final int LOCK_NAMESPACE = 120;

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardStaleness staleness;

    public DashboardRefreshRunner(NamedParameterJdbcTemplate jdbc, DashboardStaleness staleness) {
        this.jdbc = jdbc;
        this.staleness = staleness;
    }

    /**
     * 잠금을 잡은 요청 하나만 재계산한다. 못 잡으면 다른 요청이 이미 하고 있다는 뜻이라 그냥 돌아간다.
     *
     * <p>잠금을 잡은 뒤 한 번 더 확인하는 이유 — 기다리는 사이 다른 요청이 이미 끝냈을 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshOnce(long wholesalerId, Duration staleAfter, LongConsumer refresh) {
        Boolean acquired = jdbc.queryForObject("""
                select pg_try_advisory_xact_lock(:namespace, :wholesalerId)
                """, Map.of("namespace", LOCK_NAMESPACE, "wholesalerId", (int) wholesalerId), Boolean.class);

        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        if (!staleness.isStale(wholesalerId, staleAfter)) {
            return;
        }
        refresh.accept(wholesalerId);
    }
}
