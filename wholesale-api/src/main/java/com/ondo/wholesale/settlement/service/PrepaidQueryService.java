package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.settlement.dto.PrepaidSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 거래처 선수금 요약 (MUL-125) — 정산 탭 3카드(총 입금액 · 배분 완료액 · 남은 선수금).
 * 취소된 입금은 통째로, 취소된 배분은 그 줄만 뺀다. 남은 선수금은 배분 규칙이 쓰는 값과 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrepaidQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public PrepaidSummaryResponse summary(Long wholesalerId, Long retailerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("wholesalerId", wholesalerId).addValue("retailerId", retailerId);
        List<long[]> rows = jdbc.query("""
                select
                    coalesce((select sum(p.amount) from wholesale.payment p
                              where p.partner_id = pt.id and p.voided_at is null), 0) as total_paid,
                    coalesce((select sum(a.amount) from wholesale.payment_allocation a
                              join wholesale.payment p on p.id = a.payment_id
                              where p.partner_id = pt.id and p.voided_at is null
                                and a.cancelled_at is null), 0) as total_allocated
                from wholesale.partner pt
                where pt.wholesaler_id = :wholesalerId and pt.retailer_id = :retailerId
                """, params, (rs, i) -> new long[]{rs.getLong("total_paid"), rs.getLong("total_allocated")});
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("거래처가 없거나 접근할 수 없습니다.");
        }
        long paid = rows.getFirst()[0];
        long allocated = rows.getFirst()[1];
        return new PrepaidSummaryResponse(retailerId, Math.toIntExact(paid), Math.toIntExact(allocated),
                Math.toIntExact(paid - allocated));
    }
}
