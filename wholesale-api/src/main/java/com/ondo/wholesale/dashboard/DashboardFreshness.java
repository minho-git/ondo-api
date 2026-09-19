package com.ondo.wholesale.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
 *
 * <p>갱신은 읽기 트랜잭션 밖에서 따로 커밋한다({@code REQUIRES_NEW}) — 조회는 읽기 전용이고,
 * 갱신이 실패해도 화면은 오래된 값으로 떠야 한다.
 */
@Component
public class DashboardFreshness {

    private static final Logger log = LoggerFactory.getLogger(DashboardFreshness.class);

    /** advisory lock 의 첫 번째 키 — 다른 기능이 쓰는 잠금과 겹치지 않게 대시보드 전용 값을 둔다. */
    private static final int LOCK_NAMESPACE = 120;

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardFreshness(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 마지막 갱신이 {@code staleAfter} 보다 오래됐으면 {@code refresh} 를 부른다.
     *
     * <p>갱신 중 실패해도 예외를 밖으로 내지 않는다. 대시보드는 숫자가 조금 오래되는 것보다
     * 화면이 안 뜨는 쪽이 나쁘다.
     */
    public void refreshIfStale(long wholesalerId, Duration staleAfter, LongConsumer refresh) {
        if (!isStale(wholesalerId, staleAfter)) {
            return;
        }
        try {
            refreshOnce(wholesalerId, staleAfter, refresh);
        } catch (RuntimeException e) {
            log.warn("대시보드 요약 갱신 실패. 오래된 값으로 응답한다. wholesalerId={}", wholesalerId, e);
        }
    }

    private boolean isStale(long wholesalerId, Duration staleAfter) {
        List<OffsetDateTime> rows = jdbc.query("""
                select refreshed_at from wholesale.dashboard_counter where wholesaler_id = :wholesalerId
                """, Map.of("wholesalerId", wholesalerId),
                (rs, rowNum) -> rs.getObject("refreshed_at", OffsetDateTime.class));

        // 한 번도 계산한 적 없으면 지금 만든다
        return rows.isEmpty() || rows.getFirst().isBefore(OffsetDateTime.now().minus(staleAfter));
    }

    /**
     * 잠금을 잡은 요청 하나만 재계산한다. 못 잡으면 다른 요청이 이미 하고 있다는 뜻이라 그냥 돌아간다.
     *
     * <p>잠금을 잡은 뒤 한 번 더 확인하는 이유 — 기다리는 사이 다른 요청이 이미 끝냈을 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void refreshOnce(long wholesalerId, Duration staleAfter, LongConsumer refresh) {
        Boolean acquired = jdbc.queryForObject("""
                select pg_try_advisory_xact_lock(:namespace, :wholesalerId)
                """, Map.of("namespace", LOCK_NAMESPACE, "wholesalerId", (int) wholesalerId), Boolean.class);

        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        if (!isStale(wholesalerId, staleAfter)) {
            return;
        }
        refresh.accept(wholesalerId);
    }
}
