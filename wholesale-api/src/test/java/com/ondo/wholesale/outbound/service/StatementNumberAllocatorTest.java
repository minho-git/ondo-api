package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장끼번호 채번 검증 (MUL-49 · D-076) — 같은 날은 이어서, 날짜가 바뀌면 1부터.
 * 날짜 경계는 KST 다. 채번에 쓴 시각이 그대로 shippedAt 이 되어야 표시 코드
 * (JG-YYYYMMDD-NNN)와 날짜가 어긋나지 않는다 — issuedAt 을 함께 돌려주는 이유.
 */
@SpringBootTest
@Transactional
class StatementNumberAllocatorTest extends PostgresTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired StatementNumberAllocator allocator;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 같은_날은_이어서_채번한다() {
        long wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "statement-same@ondo.test", "9500000033");
        LocalDate today = LocalDate.now(KST);
        jdbc.update("update wholesale.wholesaler set last_statement_date = ?, last_statement_seq = 4 where id = ?",
                today, wholesalerId);

        StatementNumberAllocator.Issued issued = allocator.next(wholesalerId);

        assertThat(issued.statementNumber()).isEqualTo(5);
        assertThat(issued.issuedAt().atZoneSameInstant(KST).toLocalDate()).isEqualTo(today);
    }

    @Test
    void 날짜가_바뀌면_1부터_다시_센다() {
        long wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "statement-next@ondo.test", "9500000034");
        LocalDate today = LocalDate.now(KST);
        jdbc.update("update wholesale.wholesaler set last_statement_date = ?, last_statement_seq = 7 where id = ?",
                today.minusDays(1), wholesalerId);

        StatementNumberAllocator.Issued issued = allocator.next(wholesalerId);

        assertThat(issued.statementNumber()).isEqualTo(1);
        // 채번 날짜도 오늘로 넘어간다 — 다음 채번이 이어서 세는 기준
        assertThat(jdbc.queryForObject("select last_statement_date from wholesale.wholesaler where id = ?",
                LocalDate.class, wholesalerId)).isEqualTo(today);
    }
}
