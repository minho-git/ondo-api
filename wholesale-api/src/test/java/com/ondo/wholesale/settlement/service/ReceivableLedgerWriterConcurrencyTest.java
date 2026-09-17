package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.settlement.domain.ReceivableEntryType;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 거래처에 원장을 동시에 써도 잔액이 어긋나지 않는다 (MUL-123).
 *
 * <p>락이 없으면 두 요청이 같은 잔액을 읽고 각자 더해 한쪽이 사라진다. 요청마다 제 트랜잭션을
 * 커밋해야 락 경합이 생기므로 테스트 트랜잭션으로 감싸지 않는다 — 넣은 데이터는 끝나고 직접 지운다.
 * 다른 테스트가 원장 전체 행 수를 세기 때문이다.
 */
@SpringBootTest
class ReceivableLedgerWriterConcurrencyTest extends PostgresTestSupport {

    private static final int REQUESTS = 8;
    private static final long AMOUNT = 1000L;

    @Autowired ReceivableLedgerWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private long wholesalerId;
    private long partnerId;

    @BeforeEach
    void 거래처를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "ledger-concurrency@ondo.test", "9500000124");
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 1241L, "동시상회");
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from wholesale.receivable_ledger where partner_id = ?", partnerId);
        jdbc.update("delete from wholesale.partner where id = ?", partnerId);
        jdbc.update("delete from wholesale.wholesaler where id = ?", wholesalerId);
    }

    @Test
    void 동시에_써도_잔액이_하나도_빠지지_않는다() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    tx.executeWithoutResult(status -> writer.append(partnerId, OffsetDateTime.now(),
                            List.of(new ReceivableLedgerWriter.Line(ReceivableEntryType.ADJUST, AMOUNT,
                                    UUID.randomUUID().toString(), null, null, null, "동시 조정"))));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long expected = REQUESTS * AMOUNT;
        assertThat(jdbc.queryForObject(
                "select receivable_balance from wholesale.partner where id = ?", Long.class, partnerId))
                .isEqualTo(expected);
        // 쓴 순서대로 1000, 2000, … — 두 요청이 같은 잔액을 읽었다면 값이 겹친다
        assertThat(jdbc.queryForList(
                "select balance_after from wholesale.receivable_ledger where partner_id = ? order by id",
                Long.class, partnerId))
                .containsExactlyElementsOf(LongStream.rangeClosed(1, REQUESTS).map(n -> n * AMOUNT).boxed().toList());
    }
}
