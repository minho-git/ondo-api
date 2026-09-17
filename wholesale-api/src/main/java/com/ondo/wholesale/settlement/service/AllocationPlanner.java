package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.settlement.domain.PaymentAllocation;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest.PaymentAllocationRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.repository.PaymentAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 입금을 주문에 붙이는 규칙 (MUL-124 · MUL-125) — 입금 등록과 선수금 정산이 같은 검사 · 같은 순서를 쓴다.
 *
 * <p>규칙은 셋이다.
 * <ul>
 *   <li>출고된 금액에만 붙인다. 주문별 상한 = 원장 OUTBOUND 합 − 이미 붙은 배분(취소 · 무효 입금 제외)</li>
 *   <li>돈은 앞에 놓인 입금부터 꺼낸다. 선수금은 오래된 입금부터 — 입금 등록이면 이번 입금을 맨 앞에 둔다</li>
 *   <li>배분 줄은 항상 특정 입금을 가리킨다. 한 주문이 입금 여럿에 걸치면 줄이 여럿이다 —
 *       입금을 취소하면 그 입금의 배분만 계산에서 빠져야 해서다</li>
 * </ul>
 *
 * <p>거래처 행 락 아래서만 돈다. 두 요청이 같은 주문의 남은 미수나 같은 선수금을 각자 읽고 넘치게
 * 쓰지 못하게 하는 게 그 락이다 — 원장 쓰기와 같은 락이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AllocationPlanner {

    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentAllocationRepository allocationRepository;

    public record PartnerRow(long id, long retailerId, String retailerName) {}

    public record OrderRow(long id, long partnerId, String status, int orderNumber) {}

    /** 돈을 꺼낼 입금 하나와 거기 남은 금액. */
    public record Source(long paymentId, long remaining) {}

    /** 400 — 요청 줄 자체의 결함. 비어 있는 건 부르는 쪽이 정한다(입금은 비어도 되고 정산은 안 된다). */
    public static void validateLines(List<PaymentAllocationRequest> lines) {
        Set<Long> orderIds = new HashSet<>();
        for (PaymentAllocationRequest line : lines) {
            if (line == null || line.orderId() == null) {
                throw ApiException.validationFailed("allocations.orderId", "필수입니다.");
            }
            if (line.amount() == null || line.amount() <= 0) {
                throw ApiException.validationFailed("allocations.amount", "0보다 커야 합니다.");
            }
            if (!orderIds.add(line.orderId())) {
                throw new ApiException(ErrorCode.DUPLICATE_ORDER);
            }
        }
    }

    public static long total(List<PaymentAllocationRequest> lines) {
        return lines.stream().mapToLong(PaymentAllocationRequest::amount).sum();
    }

    /**
     * 응답 시각은 UTC 로 낸다. 멱등 재요청은 저장한 JSON 을 다시 읽는데 Jackson 이 UTC 로 바꿔서,
     * 첫 응답이 +09:00 이면 같은 순간인데도 본문이 달라진다.
     */
    public static OffsetDateTime utc(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** 거래처 행 락 — 원장 쓰기와 같은 락이라 같은 트랜잭션 안에서 다시 잡아도 그대로다. */
    public PartnerRow lockPartner(Long wholesalerId, Long retailerId) {
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

    /** 붙일 주문이 이 거래처의 확정 주문이고, 줄마다 남은 미수 안인지 본다. */
    public Map<Long, OrderRow> checkOrders(Long wholesalerId, long partnerId, List<PaymentAllocationRequest> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = lines.stream().map(PaymentAllocationRequest::orderId).toList();
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

        for (PaymentAllocationRequest line : lines) {
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

    /** 선수금 — 취소 안 된 입금마다 아직 안 붙은 돈. 오래된 입금부터(같은 시각이면 먼저 등록한 것). */
    public List<Source> prepaidSources(long partnerId) {
        return jdbc.query("""
                select p.id,
                       p.amount - coalesce(sum(a.amount) filter (where a.cancelled_at is null), 0) as remaining
                from wholesale.payment p
                left join wholesale.payment_allocation a on a.payment_id = p.id
                where p.partner_id = :partnerId and p.voided_at is null
                group by p.id, p.amount, p.paid_at
                having p.amount - coalesce(sum(a.amount) filter (where a.cancelled_at is null), 0) > 0
                order by p.paid_at, p.id
                """, new MapSqlParameterSource("partnerId", partnerId),
                (rs, i) -> new Source(rs.getLong("id"), rs.getLong("remaining")));
    }

    public static long sum(List<Source> sources) {
        return sources.stream().mapToLong(Source::remaining).sum();
    }

    /**
     * 요청 줄 순서대로, 앞에 놓인 입금부터 꺼내 배분 줄을 쓴다. 부르는 쪽이 합계가 {@code sources} 안이라는 걸
     * 이미 확인했다 — 모자라면 구현 오류라 멈춘다.
     */
    public List<PaymentCreatedResponse.Allocation> allocate(List<Source> sources,
                                                            List<PaymentAllocationRequest> lines,
                                                            Map<Long, OrderRow> orders) {
        long[] left = sources.stream().mapToLong(Source::remaining).toArray();
        int cursor = 0;
        List<PaymentCreatedResponse.Allocation> written = new ArrayList<>();
        for (PaymentAllocationRequest line : lines) {
            long need = line.amount();
            while (need > 0) {
                while (cursor < left.length && left[cursor] == 0) {
                    cursor++;
                }
                if (cursor == left.length) {
                    throw new IllegalStateException("배분 합계 검사를 통과했는데 꺼낼 돈이 모자란다");
                }
                long take = Math.min(need, left[cursor]);
                PaymentAllocation saved = allocationRepository.save(
                        new PaymentAllocation(sources.get(cursor).paymentId(), line.orderId(), take));
                written.add(new PaymentCreatedResponse.Allocation(saved.getId(), saved.getOrderId(),
                        orders.get(saved.getOrderId()).orderNumber(), saved.getPaymentId(),
                        Math.toIntExact(take), utc(saved.getCreatedAt())));
                left[cursor] -= take;
                need -= take;
            }
        }
        return written;
    }

    private Map<Long, Long> sumByOrder(String sql, MapSqlParameterSource params) {
        Map<Long, Long> totals = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            totals.put(rs.getLong("order_id"), rs.getLong("total"));
        });
        return totals;
    }
}
