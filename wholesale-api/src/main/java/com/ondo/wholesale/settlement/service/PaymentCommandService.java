package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.settlement.LedgerSign;
import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.Payment;
import com.ondo.wholesale.settlement.domain.PaymentAllocation;
import com.ondo.wholesale.settlement.domain.PaymentIdempotency;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest.PaymentAllocationRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.repository.PaymentAllocationRepository;
import com.ondo.wholesale.settlement.repository.PaymentIdempotencyRepository;
import com.ondo.wholesale.settlement.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 입금 등록 (MUL-124) — 화면의 [입금만 진행]과 [입금 및 정산]이 같은 요청이다. 배분이 비면 선수금.
 *
 * <p>한 트랜잭션: 멱등 확인 → 요청 검사 → 거래처 락 → 배분 검사 → 입금 · 원장 · 배분 저장 → 응답 저장.
 * 배분 검사를 거래처 락 아래서 하는 이유는 동시에 들어온 두 입금이 같은 주문의 남은 미수를 각자
 * 읽고 넘치게 붙이지 못하게 하려는 것이다 — 배분할 주문은 전부 이 거래처 주문이라 락 하나로 충분하다.
 *
 * <p>배분 상한은 이번 입금액이다. 남은 선수금까지 합쳐 쓰는 건 MUL-125 가 넓힌다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    /** 폼 시계가 서버보다 조금 빠른 건 봐준다 — 몇 초 앞선 "지금"을 미래 입금으로 거절하지 않게. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentIdempotencyRepository idempotencyRepository;
    private final ReceivableLedgerWriter ledgerWriter;
    private final ObjectMapper objectMapper;

    /** {@code replayed}가 참이면 같은 키 재요청 — 컨트롤러가 201 대신 200 을 내린다. */
    public record PaymentResult(PaymentCreatedResponse response, boolean replayed) {}

    public PaymentResult create(Long wholesalerId, String idempotencyKey, PaymentCreateRequest request) {
        validate(idempotencyKey, request);
        List<PaymentAllocationRequest> allocations = allocationsOf(request);
        String requestHash = requestHash(request, allocations);

        Optional<PaymentIdempotency> existing = idempotencyRepository.findById(
                new PaymentIdempotency.Key(wholesalerId, idempotencyKey));
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new PaymentResult(objectMapper.readValue(
                    existing.get().getResponseBody(), PaymentCreatedResponse.class), true);
        }

        PartnerRow partner = lockPartner(wholesalerId, request.retailerId());
        Map<Long, OrderRow> orders = checkAllocations(wholesalerId, partner.id(), allocations);

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

        List<PaymentCreatedResponse.Allocation> allocated = new ArrayList<>();
        long allocatedTotal = 0;
        for (PaymentAllocationRequest line : allocations) {
            PaymentAllocation saved = allocationRepository.save(
                    new PaymentAllocation(payment.getId(), line.orderId(), line.amount()));
            allocatedTotal += saved.getAmount();
            allocated.add(new PaymentCreatedResponse.Allocation(saved.getId(), saved.getOrderId(),
                    orders.get(saved.getOrderId()).orderNumber(), Math.toIntExact(saved.getAmount()),
                    utc(saved.getCreatedAt())));
        }

        PaymentCreatedResponse response = new PaymentCreatedResponse(
                payment.getId(), partner.retailerId(), partner.retailerName(),
                Math.toIntExact(payment.getAmount()), utc(payment.getPaidAt()), payment.getPaidBy(),
                payment.getMethod(), payment.getMemo(),
                Math.toIntExact(payment.getAmount() - allocatedTotal), allocated,
                LedgerSign.toWholesaleScreen(ledger.getLast().getBalanceAfter()),
                utc(payment.getCreatedAt()));
        idempotencyRepository.save(new PaymentIdempotency(wholesalerId, idempotencyKey, requestHash,
                payment.getId(), objectMapper.writeValueAsString(response)));
        return new PaymentResult(response, false);
    }

    /**
     * 응답 시각은 UTC 로 맞춘다. 저장한 응답을 다시 읽으면 Jackson 이 UTC 로 바꿔서, 첫 응답이 +09:00 이면
     * 같은 순간인데도 재요청 본문이 달라진다 — 계약은 "같은 키 재요청은 동일 본문"이다.
     */
    private static OffsetDateTime utc(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC);
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
        Set<Long> orderIds = new HashSet<>();
        long total = 0;
        for (PaymentAllocationRequest line : allocationsOf(request)) {
            if (line == null || line.orderId() == null) {
                throw ApiException.validationFailed("allocations.orderId", "필수입니다.");
            }
            if (line.amount() == null || line.amount() <= 0) {
                throw ApiException.validationFailed("allocations.amount", "0보다 커야 합니다.");
            }
            if (!orderIds.add(line.orderId())) {
                throw new ApiException(ErrorCode.DUPLICATE_ORDER);
            }
            total += line.amount();
        }
        if (total > request.amount()) {
            throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_PAYMENT);
        }
    }

    private static List<PaymentAllocationRequest> allocationsOf(PaymentCreateRequest request) {
        return request.allocations() == null ? List.of() : request.allocations();
    }

    private record PartnerRow(long id, long retailerId, String retailerName) {}

    /** 거래처 행 락 — 원장 쓰기와 같은 락이라 같은 트랜잭션 안에서 다시 잡아도 그대로다. */
    private PartnerRow lockPartner(Long wholesalerId, Long retailerId) {
        List<PartnerRow> rows = jdbc.query("""
                select id, retailer_id, retailer_name from wholesale.partner
                where wholesaler_id = :wholesalerId and retailer_id = :retailerId
                for update
                """, new MapSqlParameterSource()
                        .addValue("wholesalerId", wholesalerId).addValue("retailerId", retailerId),
                (rs, i) -> new PartnerRow(rs.getLong("id"), rs.getLong("retailer_id"),
                        rs.getString("retailer_name")));
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("거래처가 없거나 접근할 수 없습니다.");
        }
        return rows.getFirst();
    }

    private record OrderRow(long id, long partnerId, String status, int orderNumber) {}

    /**
     * 거래처 락 아래서 본다. 남은 미수 = 출고로 생긴 미수(원장 OUTBOUND) − 이미 붙은 배분
     * (취소된 배분 · 무효 입금의 배분 제외). 출고 전 주문은 0 이라 붙일 수 없다.
     */
    private Map<Long, OrderRow> checkAllocations(Long wholesalerId, long partnerId,
                                                  List<PaymentAllocationRequest> allocations) {
        if (allocations.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = allocations.stream().map(PaymentAllocationRequest::orderId).toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", orderIds).addValue("wholesalerId", wholesalerId);

        Map<Long, OrderRow> orders = new HashMap<>();
        jdbc.query("""
                select id, partner_id, status, order_number from wholesale.orders
                where id in (:ids) and wholesaler_id = :wholesalerId
                """, params, rs -> {
            orders.put(rs.getLong("id"), new OrderRow(rs.getLong("id"), rs.getLong("partner_id"),
                    rs.getString("status"), rs.getInt("order_number")));
        });

        Map<Long, Long> shipped = sumByOrder("""
                select order_id, sum(delta) as total from wholesale.receivable_ledger
                where order_id in (:ids) and entry_type = 'OUTBOUND'
                group by order_id
                """, params);
        Map<Long, Long> allocated = sumByOrder("""
                select a.order_id, sum(a.amount) as total from wholesale.payment_allocation a
                join wholesale.payment p on p.id = a.payment_id
                where a.order_id in (:ids) and a.cancelled_at is null and p.voided_at is null
                group by a.order_id
                """, params);

        for (PaymentAllocationRequest line : allocations) {
            OrderRow order = orders.get(line.orderId());
            if (order == null) {
                throw new ResourceNotFoundException("주문이 없거나 접근할 수 없습니다.");
            }
            if (order.partnerId() != partnerId) {
                throw new ApiException(ErrorCode.ORDER_RETAILER_MISMATCH);
            }
            if (!"CONFIRMED".equals(order.status())) {
                throw new ApiException(ErrorCode.ORDER_NOT_CONFIRMED);
            }
            long outstanding = shipped.getOrDefault(order.id(), 0L) - allocated.getOrDefault(order.id(), 0L);
            if (line.amount() > outstanding) {
                throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_OUTSTANDING,
                        "주문의 남은 미수는 " + Math.max(outstanding, 0) + "원입니다.");
            }
        }
        return orders;
    }

    private Map<Long, Long> sumByOrder(String sql, MapSqlParameterSource params) {
        Map<Long, Long> totals = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            totals.put(rs.getLong("order_id"), rs.getLong("total"));
        });
        return totals;
    }

    /** 고정 필드 순서 직렬화의 SHA-256 — "같은 본문"의 지문. 배분 순서는 응답 순서라 보존한다. */
    private static String requestHash(PaymentCreateRequest request, List<PaymentAllocationRequest> allocations) {
        StringBuilder canonical = new StringBuilder()
                .append("retailerId=").append(request.retailerId())
                .append("|amount=").append(request.amount())
                .append("|paidAt=").append(request.paidAt().toInstant())
                .append("|paidBy=").append(request.paidBy())
                .append("|method=").append(request.method())
                .append("|memo=").append(request.memo() == null ? "" : request.memo());
        for (PaymentAllocationRequest line : allocations) {
            canonical.append('|').append(line.orderId()).append(':').append(line.amount());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 은 JVM 표준 알고리즘이다", e);
        }
    }
}
