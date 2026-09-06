package com.ondo.wholesale.outbound.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 장끼번호 채번 (D-076: 도매처별, 같은 날은 이어서 · 날짜가 바뀌면 1부터. 경계는 KST).
 *
 * <p>UPDATE 가 wholesaler 행 락을 잡아 직렬화된다 — {@code OutboundNumberAllocator}와
 * 같은 관행이고 락 순서 규약도 같다(출고 확정의 마지막: outbound 행 → 주문 행 →
 * variant 행 → 이 락). 채번에 쓴 시각({@link Issued#issuedAt()})을 shippedAt 에
 * 그대로 저장해야 표시 코드(JG-YYYYMMDD-NNN)와 날짜가 어긋나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StatementNumberAllocator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 채번 결과 — 번호와, 그 번호의 날짜 기준이 된 시각. */
    public record Issued(int statementNumber, OffsetDateTime issuedAt) {
    }

    private final JdbcTemplate jdbc;

    public Issued next(Long wholesalerId) {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate kstDay = now.atZoneSameInstant(KST).toLocalDate();
        int statementNumber = jdbc.queryForObject("""
                update wholesale.wholesaler
                   set last_statement_seq = case when last_statement_date = ?
                                                 then last_statement_seq + 1 else 1 end,
                       last_statement_date = ?,
                       updated_at = now()
                 where id = ?
                returning last_statement_seq
                """, Integer.class, kstDay, kstDay, wholesalerId);
        return new Issued(statementNumber, now);
    }
}
