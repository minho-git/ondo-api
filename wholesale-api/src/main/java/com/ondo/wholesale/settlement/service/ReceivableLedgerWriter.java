package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.ReceivableEntryType;
import com.ondo.wholesale.settlement.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 미수 원장에 행을 쓰는 유일한 자리 (MUL-123) — 출고 확정 · 입금 · 입금 취소 · 조정이 전부 여기를 거친다.
 *
 * <p>한 번에 하는 일은 넷이다: 거래처 행 락 → {@code partner.receivable_balance} 읽기 →
 * 행 추가({@code balance_after}를 이어서) → {@code receivable_balance} 갱신. 거래처 행 락이
 * 같은 거래처의 쓰기를 한 줄로 세우므로 잔액이 어긋나지 않는다. 락 안에서는 이것 말고 아무것도
 * 하지 않는다 — 뒤에 선 요청이 그만큼 기다린다.
 *
 * <p>{@code receivable_balance}는 원장 마지막 {@code balance_after}를 베껴 둔 값이다. 미수 목록의
 * 정렬·페이지가 이 칸을 읽는다. 원장을 여기 말고 다른 데서 쓰면 둘이 어긋나므로 쓰는 곳을 하나로 둔다.
 *
 * <p>부르는 쪽의 트랜잭션 안에서만 돈다 — 원장만 커밋되고 원래 일이 롤백되는 일이 없게.
 */
@Component
@RequiredArgsConstructor
public class ReceivableLedgerWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * 원장 한 행의 재료. 부호는 종류가 정한다 — 틀리면 DB({@code receivable_ledger_sign_ck})가 거절한다.
     */
    public record Line(ReceivableEntryType entryType, long delta, String requestId,
                       Long orderId, Long outboundId, Long paymentId, String memo) {

        /** 출고로 미수가 생긴다(+). 주문마다 한 행. */
        public static Line outbound(long orderId, long outboundId, long amount) {
            return new Line(ReceivableEntryType.OUTBOUND, amount,
                    "OUTBOUND-" + outboundId + "-" + orderId, orderId, outboundId, null, null);
        }

        /** 입금 취소로 갚을 돈이 다시 늘어난다(+). 취소한 입금을 가리킨다 (MUL-127). */
        public static Line paymentVoid(long paymentId, long amount) {
            return new Line(ReceivableEntryType.PAYMENT_VOID, amount,
                    "PAYMENT_VOID-" + paymentId, null, null, paymentId, null);
        }

        /** 입금으로 갚을 돈이 줄어든다(−). 주문을 가리키지 않는다 — 어느 주문 값인지는 배분이 적는다. */
        public static Line payment(long paymentId, long amount) {
            return new Line(ReceivableEntryType.PAYMENT, -amount,
                    "PAYMENT-" + paymentId, null, null, paymentId, null);
        }
    }

    /**
     * 같은 거래처의 행들을 순서대로 쓴다. 돌려주는 행의 마지막 {@code balanceAfter}가 새 잔액이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LedgerEntry> append(long partnerId, OffsetDateTime occurredAt, List<Line> lines) {
        long balance = lockBalance(partnerId);
        List<LedgerEntry> written = new ArrayList<>(lines.size());
        for (Line line : lines) {
            balance += line.delta();
            written.add(ledgerEntryRepository.save(LedgerEntry.builder()
                    .partnerId(partnerId)
                    .requestId(line.requestId())
                    .entryType(line.entryType())
                    .delta(line.delta())
                    .balanceAfter(balance)
                    .orderId(line.orderId())
                    .outboundId(line.outboundId())
                    .paymentId(line.paymentId())
                    .memo(line.memo())
                    .occurredAt(occurredAt)
                    .build()));
        }
        jdbc.update("update wholesale.partner set receivable_balance = :balance where id = :id",
                new MapSqlParameterSource().addValue("balance", balance).addValue("id", partnerId));
        return written;
    }

    private long lockBalance(long partnerId) {
        return jdbc.queryForObject(
                "select receivable_balance from wholesale.partner where id = :id for update",
                new MapSqlParameterSource("id", partnerId), Long.class);
    }
}
