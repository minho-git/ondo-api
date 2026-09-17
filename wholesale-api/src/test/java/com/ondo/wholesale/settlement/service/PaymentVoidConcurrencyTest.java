package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.settlement.dto.PaymentVoidRequest;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 입금을 동시에 두 번 취소해도 반대 줄은 하나다 (MUL-127). 둘 다 통과하면 미수가 입금액만큼 더 늘어난다.
 * 요청마다 커밋해야 락 경합이 생기므로 테스트 트랜잭션으로 감싸지 않고 끝나고 직접 지운다.
 */
@SpringBootTest
class PaymentVoidConcurrencyTest extends PostgresTestSupport {

    @Autowired SettlementCancelService cancelService;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long paymentId;

    @BeforeEach
    void 입금을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "payment-void-concurrency@ondo.test", "9500000131");
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 501L, "동시취소상회");
        paymentId = jdbc.queryForObject("""
                insert into wholesale.payment (partner_id, wholesaler_id, request_id, amount, paid_by, method, paid_at)
                values (?, ?, ?, 100000, 'RETAILER', 'CASH', now()) returning id
                """, Long.class, partnerId, wholesalerId, UUID.randomUUID().toString());
        jdbc.update("""
                insert into wholesale.receivable_ledger
                    (partner_id, request_id, entry_type, delta, balance_after, payment_id, occurred_at)
                values (?, ?, 'PAYMENT', -100000, -100000, ?, now())
                """, partnerId, "PAYMENT-" + paymentId, paymentId);
        jdbc.update("update wholesale.partner set receivable_balance = -100000 where id = ?", partnerId);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from wholesale.receivable_ledger where partner_id = ?", partnerId);
        jdbc.update("delete from wholesale.payment where id = ?", paymentId);
        jdbc.update("delete from wholesale.partner where id = ?", partnerId);
        jdbc.update("delete from wholesale.wholesaler where id = ?", wholesalerId);
    }

    @Test
    void 동시에_두_번_취소하면_하나만_되고_하나는_409다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        cancelService.voidPayment(wholesalerId, paymentId, new PaymentVoidRequest("동시 취소"));
                        return "OK";
                    } catch (ApiException e) {
                        return e.errorCode().name();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("OK", ErrorCode.STATE_CONFLICT.name());
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from wholesale.receivable_ledger where entry_type = 'PAYMENT_VOID' and payment_id = ?",
                Integer.class, paymentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select receivable_balance from wholesale.partner where id = ?", Long.class, partnerId)).isZero();
    }
}
