package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.settlement.LedgerSign;
import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.Payment;
import com.ondo.wholesale.settlement.domain.PaymentAllocation;
import com.ondo.wholesale.settlement.dto.AllocationCancelledResponse;
import com.ondo.wholesale.settlement.dto.PaymentVoidRequest;
import com.ondo.wholesale.settlement.dto.PaymentVoidedResponse;
import com.ondo.wholesale.settlement.repository.PaymentAllocationRepository;
import com.ondo.wholesale.settlement.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입금 취소 · 배분 취소 (MUL-127) — 사장이 통장을 보고 손으로 적는 기록이라 잘못 넣는 일이 생긴다.
 * 돈 기록은 지우지 않는다. 표시만 찍고 필요한 반대 줄을 쌓는다.
 *
 * <ul>
 *   <li><b>배분 취소</b> — {@code cancelled_at}만 찍는다. 배분을 읽는 곳이 전부 취소된 줄을 빼고 세서
 *       그 금액은 저절로 선수금으로 돌아간다. 원장은 원래 배분과 상관없다.</li>
 *   <li><b>입금 취소</b> — {@code voided_at}을 찍고 원장에 {@code PAYMENT_VOID}(+) 줄을 쌓는다. 원장은 줄마다
 *       잔액을 적어 두고 거래처 미수 칸에 베껴 두는 방식이라, 표시만으로는 잔액이 돌아가지 않는다.
 *       그 입금의 배분은 하나하나 취소하지 않는다 — 읽는 곳이 무효 입금의 배분을 이미 뺀다.</li>
 * </ul>
 *
 * <p>둘 다 거래처 행 락 아래서 한다. 입금 · 선수금 정산과 같은 락이라 동시에 같은 돈을 쓰거나 되돌리지 못한다.
 * 같은 것을 두 번 취소하면 409 {@code STATE_CONFLICT}다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementCancelService {

    private static final int MAX_REASON_LENGTH = 200;

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final ReceivableLedgerWriter ledgerWriter;
    private final AllocationPlanner planner;

    public PaymentVoidedResponse voidPayment(Long wholesalerId, Long paymentId, PaymentVoidRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw ApiException.validationFailed("reason", "1~" + MAX_REASON_LENGTH + "자여야 합니다.");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getWholesalerId().equals(wholesalerId))
                .orElseThrow(() -> new ResourceNotFoundException("입금이 없거나 접근할 수 없습니다."));

        planner.lockPartnerById(payment.getPartnerId());
        // 락을 잡은 뒤 DB 로 다시 본다 — 동시에 들어온 두 취소 중 뒤의 것은 여기서 진다
        if (isVoidedNow(paymentId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "이미 취소된 입금입니다.");
        }

        OffsetDateTime now = AllocationPlanner.utc(OffsetDateTime.now());
        payment.voidWith(reason, now);
        List<LedgerEntry> ledger = ledgerWriter.append(payment.getPartnerId(), now,
                List.of(ReceivableLedgerWriter.Line.paymentVoid(payment.getId(), payment.getAmount())));
        paymentRepository.flush();

        return new PaymentVoidedResponse(payment.getId(), now, reason,
                LedgerSign.toWholesaleScreen(ledger.getLast().getBalanceAfter()),
                Math.toIntExact(AllocationPlanner.sum(planner.prepaidSources(payment.getPartnerId()))));
    }

    public AllocationCancelledResponse cancelAllocation(Long wholesalerId, Long allocationId) {
        PaymentAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("배분이 없거나 접근할 수 없습니다."));
        Payment payment = paymentRepository.findById(allocation.getPaymentId())
                .filter(p -> p.getWholesalerId().equals(wholesalerId))
                .orElseThrow(() -> new ResourceNotFoundException("배분이 없거나 접근할 수 없습니다."));

        planner.lockPartnerById(payment.getPartnerId());
        if (isVoidedNow(payment.getId())) {
            // 무효 입금의 배분은 이미 안 센다 — 되돌릴 게 없다
            throw new ApiException(ErrorCode.STATE_CONFLICT, "취소된 입금의 배분입니다.");
        }
        if (isCancelledNow(allocationId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "이미 취소된 배분입니다.");
        }

        OffsetDateTime now = AllocationPlanner.utc(OffsetDateTime.now());
        allocation.cancel(now);
        allocationRepository.flush();

        return new AllocationCancelledResponse(allocation.getId(), allocation.getOrderId(),
                allocation.getPaymentId(), Math.toIntExact(allocation.getAmount()), now,
                Math.toIntExact(AllocationPlanner.sum(planner.prepaidSources(payment.getPartnerId()))));
    }

    /** 영속성 컨텍스트가 아니라 DB 를 본다 — 락을 기다리는 동안 다른 트랜잭션이 취소했을 수 있다. */
    private boolean isVoidedNow(long paymentId) {
        return planner.isPaymentVoided(paymentId);
    }

    private boolean isCancelledNow(long allocationId) {
        return planner.isAllocationCancelled(allocationId);
    }
}
