package com.ondo.wholesale.settlement.domain;

import com.ondo.wholesale.settlement.repository.LedgerEntryRepository;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.OutboundFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미수 원장 엔티티가 V1 스키마(receivable_ledger)와 맞물려 저장·재조회되는지 확인한다 (MUL-49).
 *
 * <p>출고 확정이 OUTBOUND(+) 행을 적는 첫 소비자다 — 입금·조회(정산 티켓)가 같은
 * 매핑 위에서 이어진다.
 */
@SpringBootTest
@Transactional
class LedgerEntryMappingTest extends PostgresTestSupport {

    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void 미수_원장_행을_저장하고_다시_읽는다() {
        long wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "ledger-mapping@ondo.test", "9500000040");
        long partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 761L, "원장상회");
        long orderId = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 1, "CONFIRMED",
                OffsetDateTime.now());
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, partnerId, 1);
        // timestamptz 는 마이크로초까지라 나노를 잘라야 재조회 값과 같다
        OffsetDateTime occurredAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .partnerId(partnerId)
                .requestId("OUTBOUND-" + outboundId + "-" + orderId)
                .entryType(ReceivableEntryType.OUTBOUND)
                .delta(13000L)
                .balanceAfter(13000L)
                .orderId(orderId)
                .outboundId(outboundId)
                .occurredAt(occurredAt)
                .build());
        em.flush();
        em.clear();

        LedgerEntry found = ledgerEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(found.getPartnerId()).isEqualTo(partnerId);
        assertThat(found.getRequestId()).isEqualTo("OUTBOUND-" + outboundId + "-" + orderId);
        assertThat(found.getEntryType()).isEqualTo(ReceivableEntryType.OUTBOUND);
        assertThat(found.getDelta()).isEqualTo(13000L);
        assertThat(found.getBalanceAfter()).isEqualTo(13000L);
        assertThat(found.getOrderId()).isEqualTo(orderId);
        assertThat(found.getOutboundId()).isEqualTo(outboundId);
        // OUTBOUND 행은 입금·메모·행위자가 없다
        assertThat(found.getPaymentId()).isNull();
        assertThat(found.getMemo()).isNull();
        assertThat(found.getActor()).isNull();
        assertThat(found.getOccurredAt().toInstant()).isEqualTo(occurredAt.toInstant());
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
