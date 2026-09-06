package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출고번호 채번 검증 (MUL-49 · D-075). 동시성은 UPDATE 의 wholesaler 행 락이
 * 구조적으로 직렬화한다 — ProductNumberAllocator 와 같은 관행이라 따로 돌리지 않는다.
 */
@SpringBootTest
@Transactional
class OutboundNumberAllocatorTest extends PostgresTestSupport {

    @Autowired OutboundNumberAllocator allocator;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 출고번호는_도매처별_연번이다() {
        long 갑 = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-seq-a@ondo.test", "9500000031");
        long 을 = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-seq-b@ondo.test", "9500000032");

        assertThat(allocator.next(갑)).isEqualTo(1);
        assertThat(allocator.next(갑)).isEqualTo(2);
        // 다른 도매처의 연번은 독립이다
        assertThat(allocator.next(을)).isEqualTo(1);
    }
}
