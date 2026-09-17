package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.ReceivableEntryType;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미수 원장 쓰기 (MUL-123) — 잔액 이어쓰기 · 거래처 미수 칸 · 종류별 부호 제약.
 *
 * <p>동시에 쓸 때 잔액이 어긋나지 않는지는 커밋이 필요해 {@link ReceivableLedgerWriterConcurrencyTest}에 따로 둔다.
 */
@SpringBootTest
@Transactional
class ReceivableLedgerWriterIntegrationTest extends PostgresTestSupport {

    @Autowired ReceivableLedgerWriter writer;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;

    @BeforeEach
    void 거래처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "ledger-writer@ondo.test", "9500000123");
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 1231L, "원장쓰기상회");
    }

    @Test
    void 잔액을_이어서_쓰고_거래처_미수_칸을_마지막_잔액으로_맞춘다() {
        writer.append(partnerId, OffsetDateTime.now(), List.of(조정(1000), 조정(2000)));
        List<LedgerEntry> written = writer.append(partnerId, OffsetDateTime.now(), List.of(조정(-500)));

        assertThat(jdbc.queryForList(
                "select balance_after from wholesale.receivable_ledger where partner_id = ? order by id",
                Long.class, partnerId))
                .containsExactly(1000L, 3000L, 2500L);
        assertThat(written.getLast().getBalanceAfter()).isEqualTo(2500L);
        assertThat(거래처_미수()).isEqualTo(2500L);
    }

    @Test
    void 입금_줄은_주문_없이_쓴다() {
        long paymentId = jdbc.queryForObject("""
                insert into wholesale.payment
                    (partner_id, wholesaler_id, request_id, amount, paid_by, method, paid_at)
                values (?, ?, ?, 30000, 'RETAILER', 'BANK_TRANSFER', now()) returning id
                """, Long.class, partnerId, wholesalerId, UUID.randomUUID().toString());

        writer.append(partnerId, OffsetDateTime.now(), List.of(new ReceivableLedgerWriter.Line(
                ReceivableEntryType.PAYMENT, -30000, "PAYMENT-" + paymentId, null, null, paymentId, null)));

        assertThat(jdbc.queryForObject(
                "select order_id from wholesale.receivable_ledger where payment_id = ?", Long.class, paymentId))
                .isNull();
        assertThat(거래처_미수()).isEqualTo(-30000L);
    }

    @ParameterizedTest(name = "{0} 에 {1} 은 거절된다")
    @CsvSource({
            "OUTBOUND, -1000",
            "OUTBOUND, 0",
            "PAYMENT, 1000",
            "PAYMENT, 0",
            "PAYMENT_VOID, -1000",
            "ADJUST, 0"
    })
    void 종류와_부호가_어긋나면_DB가_거절한다(String entryType, long delta) {
        assertThatThrownBy(() -> jdbc.update("""
                insert into wholesale.receivable_ledger
                    (partner_id, request_id, entry_type, delta, balance_after, occurred_at)
                values (?, ?, ?, ?, 0, now())
                """, partnerId, UUID.randomUUID().toString(), entryType, delta))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receivable_ledger_sign_ck");
    }

    private ReceivableLedgerWriter.Line 조정(long delta) {
        return new ReceivableLedgerWriter.Line(ReceivableEntryType.ADJUST, delta,
                UUID.randomUUID().toString(), null, null, null, "테스트 조정");
    }

    private long 거래처_미수() {
        return jdbc.queryForObject(
                "select receivable_balance from wholesale.partner where id = ?", Long.class, partnerId);
    }
}
