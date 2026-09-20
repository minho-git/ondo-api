package com.ondo.wholesale.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 요약이 오래됐는지 판정한다 (MUL-135).
 *
 * <p>갱신 전후로 두 번 본다 — 한 번은 잠금을 잡기 전(헛걸음을 줄이려고), 한 번은 잡은 뒤
 * (기다리는 사이 다른 요청이 끝냈을 수 있어서). 두 자리가 서로 다른 빈에 있어 판정만 떼어 둔다.
 */
@Component
public class DashboardStaleness {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardStaleness(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isStale(long wholesalerId, Duration staleAfter) {
        List<OffsetDateTime> rows = jdbc.query("""
                select refreshed_at from wholesale.dashboard_counter where wholesaler_id = :wholesalerId
                """, Map.of("wholesalerId", wholesalerId),
                (rs, rowNum) -> rs.getObject("refreshed_at", OffsetDateTime.class));

        // 한 번도 계산한 적 없으면 지금 만든다
        return rows.isEmpty() || rows.getFirst().isBefore(OffsetDateTime.now().minus(staleAfter));
    }
}
