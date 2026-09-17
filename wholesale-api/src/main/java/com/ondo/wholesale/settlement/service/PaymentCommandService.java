package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.settlement.LedgerSign;
import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.Payment;
import com.ondo.wholesale.settlement.domain.PaymentIdempotency;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest.PaymentAllocationRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.repository.PaymentIdempotencyRepository;
import com.ondo.wholesale.settlement.repository.PaymentRepository;
import com.ondo.wholesale.settlement.service.AllocationPlanner.OrderRow;
import com.ondo.wholesale.settlement.service.AllocationPlanner.PartnerRow;
import com.ondo.wholesale.settlement.service.AllocationPlanner.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 입금 등록 (MUL-124) — 화면의 [입금만 진행]과 [입금 및 정산]이 같은 요청이다. 배분이 비면 선수금.
 *
 * <p>한 트랜잭션: 멱등 확인 → 요청 검사 → 거래처 락 → 배분 검사 → 입금 · 원장 · 배분 저장 → 응답 저장.
 * 배분 규칙(출고분만 · 앞에 놓인 입금부터 · 락 아래서)은 {@link AllocationPlanner}가 선수금 정산과 같이 쓴다.
 *
 * <p>배분 합계 상한은 이번 입금액 + 남은 선수금이다 (MUL-125). 이번 입금을 먼저 쓰고 모자라면 오래된 입금부터
 * 끌어 쓴다. 그래서 {@code unallocatedAmount}는 이번 입금에서 안 쓴 돈이고 음수가 되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    /** 폼 시계가 서버보다 조금 빠른 건 봐준다 — 몇 초 앞선 "지금"을 미래 입금으로 거절하지 않게. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository idempotencyRepository;
    private final ReceivableLedgerWriter ledgerWriter;
    private final AllocationPlanner planner;
    private final ObjectMapper objectMapper;

    /** {@code replayed}가 참이면 같은 키 재요청 — 컨트롤러가 201 대신 200 을 내린다. */
    public record PaymentResult(PaymentCreatedResponse response, boolean replayed) {}

    public PaymentResult create(Long wholesalerId, String idempotencyKey, PaymentCreateRequest request) {
        validate(idempotencyKey, request);
        List<PaymentAllocationRequest> lines = request.allocations() == null ? List.of() : request.allocations();
        String requestHash = new RequestFingerprint()
                .field("retailerId", request.retailerId())
                .field("amount", request.amount())
                .field("paidAt", request.paidAt().toInstant())
                .field("paidBy", request.paidBy())
                .field("method", request.method())
                .field("memo", request.memo())
                .lines(lines)
                .sha256Hex();

        Optional<PaymentIdempotency> existing = idempotencyRepository.findById(
                new PaymentIdempotency.Key(wholesalerId, idempotencyKey));
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new PaymentResult(objectMapper.readValue(
                    existing.get().getResponseBody(), PaymentCreatedResponse.class), true);
        }

        PartnerRow partner = planner.lockPartner(wholesalerId, request.retailerId());
        Map<Long, OrderRow> orders = planner.checkOrders(wholesalerId, partner.id(), lines);
        // 새 입금을 저장하기 전에 읽는다 — 이번 입금이 선수금 목록에 섞이지 않게
        List<Source> prepaid = planner.prepaidSources(partner.id());
        if (AllocationPlanner.total(lines) > request.amount() + AllocationPlanner.sum(prepaid)) {
            throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_PAYMENT);
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .partnerId(partner.id())
                .wholesalerId(wholesalerId)
                .requestId(idempotencyKey)
                .amount(request.amount())
                .paidBy(request.paidBy())
                .method(request.method())
                .paidAt(request.paidAt())
                .memo(request.memo())
                .build());
        List<LedgerEntry> ledger = ledgerWriter.append(partner.id(), request.paidAt(),
                List.of(ReceivableLedgerWriter.Line.payment(payment.getId(), payment.getAmount())));

        List<Source> sources = new ArrayList<>();
        sources.add(new Source(payment.getId(), payment.getAmount()));
        sources.addAll(prepaid);
        List<PaymentCreatedResponse.Allocation> allocated = planner.allocate(sources, lines, orders);
        long fromThisPayment = allocated.stream()
                .filter(a -> a.paymentId().equals(payment.getId()))
                .mapToLong(PaymentCreatedResponse.Allocation::amount).sum();

        PaymentCreatedResponse response = new PaymentCreatedResponse(
                payment.getId(), partner.retailerId(), partner.retailerName(),
                Math.toIntExact(payment.getAmount()), AllocationPlanner.utc(payment.getPaidAt()),
                payment.getPaidBy(), payment.getMethod(), payment.getMemo(),
                Math.toIntExact(payment.getAmount() - fromThisPayment),
                Math.toIntExact(AllocationPlanner.sum(planner.prepaidSources(partner.id()))),
                allocated,
                LedgerSign.toWholesaleScreen(ledger.getLast().getBalanceAfter()),
                AllocationPlanner.utc(payment.getCreatedAt()));
        idempotencyRepository.save(new PaymentIdempotency(wholesalerId, idempotencyKey, requestHash,
                payment.getId(), objectMapper.writeValueAsString(response)));
        return new PaymentResult(response, false);
    }

    /** 400 — 요청 자체의 결함. 상태와 무관하게 언제 보내도 실패한다. */
    private void validate(String idempotencyKey, PaymentCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw ApiException.validationFailed("Idempotency-Key", "1~64자여야 합니다.");
        }
        if (request == null || request.retailerId() == null) {
            throw ApiException.validationFailed("retailerId", "필수입니다.");
        }
        if (request.amount() == null || request.amount() <= 0) {
            throw ApiException.validationFailed("amount", "0보다 커야 합니다.");
        }
        if (request.paidAt() == null) {
            throw ApiException.validationFailed("paidAt", "필수입니다.");
        }
        if (request.paidBy() == null) {
            throw ApiException.validationFailed("paidBy", "필수입니다.");
        }
        if (request.method() == null) {
            throw ApiException.validationFailed("method", "필수입니다.");
        }
        if (request.memo() != null && request.memo().length() > 255) {
            throw ApiException.validationFailed("memo", "255자 이하여야 합니다.");
        }
        if (request.paidAt().isAfter(OffsetDateTime.now().plus(CLOCK_SKEW))) {
            throw new ApiException(ErrorCode.PAID_AT_IN_FUTURE);
        }
        if (request.allocations() != null) {
            AllocationPlanner.validateLines(request.allocations());
        }
    }
}
