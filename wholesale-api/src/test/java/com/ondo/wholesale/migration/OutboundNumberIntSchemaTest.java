package com.ondo.wholesale.migration;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 — 출고번호·장끼번호가 정수 컬럼인지 검증 (MUL-49).
 *
 * <p>DTO 계약이 {@code Integer}이고 표시 코드(PKG-001 · JG-20260818-001) 조립은 프론트
 * 몫이라 varchar 로 둘 이유가 없다 — orders.order_number 전례를 따른다.
 */
@SpringBootTest
@Transactional
class OutboundNumberIntSchemaTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 출고번호와_장끼번호_컬럼이_정수다() {
        assertThat(컬럼타입("outbound_number")).isEqualTo("integer");
        assertThat(컬럼타입("statement_number")).isEqualTo("integer");
    }

    private String 컬럼타입(String column) {
        return jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'wholesale' and table_name = 'outbound' and column_name = ?
                """, String.class, column);
    }
}
