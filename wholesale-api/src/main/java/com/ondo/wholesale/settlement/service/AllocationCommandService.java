package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.settlement.domain.AllocationIdempotency;
import com.ondo.wholesale.settlement.domain.PaymentIdempotency;
import com.ondo.wholesale.settlement.dto.AllocationCreateRequest;
import com.ondo.wholesale.settlement.dto.AllocationCreatedResponse;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest.PaymentAllocationRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.repository.AllocationIdempotencyRepository;
import com.ondo.wholesale.settlement.service.AllocationPlanner.OrderRow;
import com.ondo.wholesale.settlement.service.AllocationPlanner.PartnerRow;
import com.ondo.wholesale.settlement.service.AllocationPlanner.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 선수금으로 정산 (MUL-125) — 새 입금 없이 받아 둔 돈을 출고된 주문에 붙인다. 원장은 안 바뀐다.
 *
 * <p>미송 선결제가 이 길을 쓴다. 돈이 먼저 오고 물건이 나중에 나가서, 출고하는 날엔 새 입금이 없다.
 * 돈은 오래된 입금부터 꺼낸다 — 규칙은 입금 등록과 같은 {@link AllocationPlanner}다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AllocationCommandService {

    private final AllocationIdempotencyRepository idempotencyRepository;
    private final AllocationPlanner planner;
    private final ObjectMapper objectMapper;

    /** {@code replayed}가 참이면 같은 키 재요청 — 컨트롤러가 201 대신 200 을 내린다. */
    public record AllocationResult(AllocationCreatedResponse response, boolean replayed) {}

    public AllocationResult create(Long wholesalerId, String idempotencyKey, AllocationCreateRequest request) {
        validate(idempotencyKey, request);
        List<PaymentAllocationRequest> lines = request.allocations();
        String requestHash = new RequestFingerprint()
                .field("retailerId", request.retailerId())
                .lines(lines)
                .sha256Hex();

        Optional<AllocationIdempotency> existing = idempotencyRepository.findById(
                new PaymentIdempotency.Key(wholesalerId, idempotencyKey));
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new AllocationResult(objectMapper.readValue(
                    existing.get().getResponseBody(), AllocationCreatedResponse.class), true);
        }

        PartnerRow partner = planner.lockPartner(wholesalerId, request.retailerId());
        Map<Long, OrderRow> orders = planner.checkOrders(wholesalerId, partner.id(), lines);
        List<Source> prepaid = planner.prepaidSources(partner.id());
        if (AllocationPlanner.total(lines) > AllocationPlanner.sum(prepaid)) {
            throw new ApiException(ErrorCode.ALLOCATION_EXCEEDS_PREPAID,
                    "남은 선수금은 " + AllocationPlanner.sum(prepaid) + "원입니다.");
        }

        List<PaymentCreatedResponse.Allocation> allocated = planner.allocate(prepaid, lines, orders);
        AllocationCreatedResponse response = new AllocationCreatedResponse(
                partner.retailerId(), partner.retailerName(), allocated,
                Math.toIntExact(AllocationPlanner.sum(planner.prepaidSources(partner.id()))));
        idempotencyRepository.save(new AllocationIdempotency(wholesalerId, idempotencyKey, requestHash,
                objectMapper.writeValueAsString(response)));
        return new AllocationResult(response, false);
    }

    private void validate(String idempotencyKey, AllocationCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw ApiException.validationFailed("Idempotency-Key", "1~64자여야 합니다.");
        }
        if (request == null || request.retailerId() == null) {
            throw ApiException.validationFailed("retailerId", "필수입니다.");
        }
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw ApiException.validationFailed("allocations", "1건 이상이어야 합니다.");
        }
        AllocationPlanner.validateLines(request.allocations());
    }
}
