package com.ondo.wholesale.outbound.domain;

import com.ondo.wholesale.outbound.repository.OutboundRepository;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbound 엔티티가 V8 스키마(정수 채번 컬럼)와 맞물려 저장·재조회되는지 확인한다 (MUL-49).
 */
@SpringBootTest
@Transactional
class OutboundMappingTest extends PostgresTestSupport {

    @Autowired OutboundRepository outboundRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void 엔티티가_V8_스키마와_맞물린다() {
        long wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-mapping@ondo.test", "9500000030");
        long partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 801L, "맵핑상회");

        Outbound outbound = outboundRepository.save(Outbound.builder()
                .wholesalerId(wholesalerId).partnerId(partnerId).outboundNumber(1).build());
        em.flush();
        em.clear();

        Outbound found = outboundRepository.findById(outbound.getId()).orElseThrow();
        assertThat(found.getWholesalerId()).isEqualTo(wholesalerId);
        assertThat(found.getPartnerId()).isEqualTo(partnerId);
        assertThat(found.getOutboundNumber()).isEqualTo(1);
        // NULL = 미확정(장끼) · NULL = 포장완료(출고일시) — 상태 컬럼 없음 (D-074)
        assertThat(found.getStatementNumber()).isNull();
        assertThat(found.getShippedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
