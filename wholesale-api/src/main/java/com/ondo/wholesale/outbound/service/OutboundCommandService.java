package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.inventory.service.StockLedger;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.Packing;
import com.ondo.wholesale.order.domain.PackingItem;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.order.repository.OrderRepository;
import com.ondo.wholesale.order.repository.PackingRepository;
import com.ondo.wholesale.order.repository.PartnerRepository;
import com.ondo.wholesale.order.service.PackingAssembler;
import com.ondo.wholesale.outbound.domain.Outbound;
import com.ondo.wholesale.outbound.dto.OutboundCreateRequest;
import com.ondo.wholesale.outbound.dto.OutboundCreatedResponse;
import com.ondo.wholesale.outbound.dto.OutboundDetailResponse;
import com.ondo.wholesale.outbound.repository.OutboundRepository;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.repository.VariantRepository;
import com.ondo.wholesale.settlement.domain.LedgerEntry;
import com.ondo.wholesale.settlement.domain.ReceivableEntryType;
import com.ondo.wholesale.settlement.repository.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 포장 완료(출고 생성) (MUL-49). 체크한 항목들을 한 봉투로 묶는다 — 재고 불변
 * (차감은 출고 확정). 전체 선택은 포장 id 를 유지한 채 PACKED 전이, 일부 선택은
 * 분할이다(남는 쪽 대기열 잔류, D-073).
 *
 * <p>락 순서: 주문 행(id 오름차순 — 배분취소와 직렬화) → 채번(wholesaler 행).
 * 검증은 락을 잡은 뒤의 상태로 다시 한다 — 동시 생성의 패자는 락에서 풀려난 뒤
 * PACKED 를 보게 되어 409(PACKING_NOT_READY)로 진다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OutboundCommandService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrderRepository orderRepository;
    private final PackingRepository packingRepository;
    private final OutboundRepository outboundRepository;
    private final PartnerRepository partnerRepository;
    private final VariantRepository variantRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboundNumberAllocator outboundNumberAllocator;
    private final StatementNumberAllocator statementNumberAllocator;
    private final StockLedger stockLedger;
    private final PackingAssembler packingAssembler;
    private final OutboundQueryService outboundQueryService;
    private final EntityManager em;

    public OutboundCreatedResponse create(Long wholesalerId, OutboundCreateRequest request) {
        List<Long> itemIds = validatedItemIds(request);
        List<Long> orderIds = ownedOrderIds(wholesalerId, itemIds);
        List<Order> orders = lockOrders(wholesalerId, orderIds);
        ensureNotMixed(orders);

        Map<Packing, List<PackingItem>> selection = selectionByPacking(orderIds, itemIds);
        ensurePackable(selection.keySet());

        Outbound outbound = outboundRepository.save(Outbound.builder()
                .wholesalerId(wholesalerId)
                .partnerId(orders.get(0).getPartnerId())
                .outboundNumber(outboundNumberAllocator.next(wholesalerId))
                .build());

        List<Packing> packed = new ArrayList<>();
        for (Map.Entry<Packing, List<PackingItem>> entry : selection.entrySet()) {
            packed.add(packOrSplit(entry.getKey(), entry.getValue(), outbound.getId()));
        }
        // 응답을 만드는 쪽이 variant 를 jdbc 로 읽는다 — 전이·분할을 먼저 밀어넣는다
        em.flush();
        return response(outbound, orders, packed);
    }

    /** 400 — 요청 자체의 결함은 상태와 무관하게 언제 보내도 실패한다. */
    private List<Long> validatedItemIds(OutboundCreateRequest request) {
        List<Long> ids = (request == null) ? null : request.packingItemIds();
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ErrorCode.INVARIANT_VIOLATED, "포장 항목을 1건 이상 담아야 합니다.");
        }
        if (Set.copyOf(ids).size() != ids.size()) {
            throw new ApiException(ErrorCode.DUPLICATE_PACKING_ITEM);
        }
        return ids;
    }

    /** 없는·남의·배분취소 항목은 전부 404 — 존재를 확인해 주지 않는다. */
    private List<Long> ownedOrderIds(Long wholesalerId, List<Long> itemIds) {
        List<Long> orderIds = jdbc.queryForList("""
                select pk.order_id
                from wholesale.packing_item pi
                join wholesale.packing pk on pk.id = pi.packing_id
                join wholesale.orders o   on o.id = pk.order_id
                where pi.id in (:ids) and o.wholesaler_id = :wholesalerId and pi.deleted_at is null
                """, new MapSqlParameterSource()
                        .addValue("ids", itemIds).addValue("wholesalerId", wholesalerId),
                Long.class);
        if (orderIds.size() != itemIds.size()) {
            throw new ResourceNotFoundException("포장 항목이 없거나 접근할 수 없습니다.");
        }
        return orderIds.stream().distinct().sorted().toList();
    }

    /** 주문 행 락 — id 오름차순. 배분취소·동시 출고 생성과의 직렬화 지점이다. */
    private List<Order> lockOrders(Long wholesalerId, List<Long> orderIds) {
        List<Order> orders = new ArrayList<>();
        for (Long orderId : orderIds) {
            orders.add(orderRepository.lockByIdAndWholesalerId(orderId, wholesalerId)
                    .orElseThrow(() -> new ResourceNotFoundException("포장 항목이 없거나 접근할 수 없습니다.")));
        }
        return orders;
    }

    /** 한 봉투 = 한 소매처 · 한 수령 방식 [M-3]. */
    private void ensureNotMixed(List<Order> orders) {
        if (orders.stream().map(Order::getPartnerId).distinct().count() > 1) {
            throw new ApiException(ErrorCode.RETAILER_MIXED);
        }
        if (orders.stream().map(Order::getReceiveMethod).distinct().count() > 1) {
            throw new ApiException(ErrorCode.RECEIVE_BY_MIXED);
        }
    }

    /** 락 아래서 다시 읽은 항목을 포장별로 묶는다 — 락 전에 배분취소로 사라진 항목은 404. */
    private Map<Packing, List<PackingItem>> selectionByPacking(List<Long> orderIds, List<Long> itemIds) {
        Map<Long, PackingItem> aliveById = orderIds.stream()
                .flatMap(orderId -> packingRepository.findByOrderId(orderId).stream())
                .flatMap(packing -> packing.getItems().stream())
                .filter(item -> item.getDeletedAt() == null)
                .collect(Collectors.toMap(PackingItem::getId, Function.identity()));
        Map<Packing, List<PackingItem>> selection = new LinkedHashMap<>();
        for (Long itemId : itemIds.stream().sorted().toList()) {
            PackingItem item = aliveById.get(itemId);
            if (item == null) {
                throw new ResourceNotFoundException("포장 항목이 없거나 접근할 수 없습니다.");
            }
            selection.computeIfAbsent(item.getPacking(), packing -> new ArrayList<>()).add(item);
        }
        return selection;
    }

    /** READY 가 아니거나 이미 묶인 포장은 409 — 동시 생성의 패자도 여기서 진다. */
    private void ensurePackable(Set<Packing> packings) {
        boolean blocked = packings.stream().anyMatch(
                packing -> packing.getStatus() != PackingStatus.READY || packing.getOutboundId() != null);
        if (blocked) {
            throw new ApiException(ErrorCode.PACKING_NOT_READY);
        }
    }

    /** 전체 선택 = 그 포장 그대로 전이(id 유지), 일부 선택 = 분할해 나가는 쪽만 전이. */
    private Packing packOrSplit(Packing packing, List<PackingItem> selected, Long outboundId) {
        long aliveCount = packing.getItems().stream().filter(i -> i.getDeletedAt() == null).count();
        if (selected.size() == aliveCount) {
            packing.pack(outboundId);
            return packing;
        }
        Packing departed = packing.splitOff(selected);
        departed.pack(outboundId);
        return packingRepository.save(departed);
    }

    private OutboundCreatedResponse response(Outbound outbound, List<Order> orders,
                                             List<Packing> packed) {
        Map<Long, Order> orderById = orders.stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Partner partner = partnerRepository.findById(outbound.getPartnerId()).orElseThrow();
        List<Packing> ordered = packed.stream()
                .sorted(Comparator.comparing(Packing::getId)).toList();

        int totalQty = 0;
        List<OutboundCreatedResponse.Packing> blocks = new ArrayList<>();
        for (Packing packing : ordered) {
            Order order = orderById.get(packing.getOrderId());
            totalQty += packing.getItems().stream()
                    .filter(i -> i.getDeletedAt() == null).mapToInt(PackingItem::getQty).sum();
            blocks.add(new OutboundCreatedResponse.Packing(
                    packing.getId(), order.getId(), order.getOrderNumber(), packing.getStatus(),
                    packingAssembler.itemRows(packing.getItems(), order.getItems())));
        }
        return new OutboundCreatedResponse(
                outbound.getId(), outbound.getOutboundNumber(),
                partner.getRetailerId(), partner.getRetailerName(),
                null, null, outbound.getCreatedAt(), totalQty, blocks);
    }

    /**
     * 출고 확정 (MUL-49) — 재고가 실제로 줄어드는 유일한 지점. 락 순서는
     * outbound 행 → 주문 행(id 오름차순) → variant(id 오름차순) → 채번(wholesaler 행) →
     * partner 행(미수 검산 직렬화)이다. 어느 단계든 실패하면 전건 롤백이고,
     * 확정 후에는 영구 동결 — 되돌리는 전이가 없다(가드가 보장, 플래그 없음).
     */
    public OutboundDetailResponse ship(Long wholesalerId, Long outboundId) {
        Outbound outbound = outboundRepository.lockByIdAndWholesalerId(outboundId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("출고가 없거나 접근할 수 없습니다."));
        if (outbound.getShippedAt() != null) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_ALLOWED, "이미 확정된 출고입니다.");
        }
        List<ShipLine> lines = shipLines(outboundId);
        if (lines.isEmpty()) {
            throw new ApiException(ErrorCode.OUTBOUND_EMPTY);
        }

        Map<Long, Order> orders = lockOrders(wholesalerId,
                lines.stream().map(ShipLine::orderId).distinct().sorted().toList()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, OrderItem> itemsById = orders.values().stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        // variant별 합산 — 같은 SKU 가 여러 포장에 갈라져 있어도 검증·차감·원장은 한 번이다
        Map<Long, Integer> qtyByVariant = new TreeMap<>();
        for (ShipLine line : lines) {
            qtyByVariant.merge(itemsById.get(line.orderItemId()).getVariantId(), line.qty(), Integer::sum);
        }
        Map<Long, Variant> variants = variantRepository
                .lockAllByIdIn(List.copyOf(qtyByVariant.keySet())).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));
        for (Map.Entry<Long, Integer> entry : qtyByVariant.entrySet()) {
            // isShippable 은 보장이 아니다 — 조정이 재고를 예약 밑으로 침식했을 수 있다
            if (entry.getValue() > variants.get(entry.getKey()).getStockQty()) {
                throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        // 재고 차감·FIFO 소진·평균원가 재계산·OUT 원장은 전부 StockLedger 한 몸으로
        for (Map.Entry<Long, Integer> entry : qtyByVariant.entrySet()) {
            stockLedger.recordOutbound(variants.get(entry.getKey()), entry.getValue(),
                    "OUTBOUND", outboundId);
        }
        for (ShipLine line : lines) {
            itemsById.get(line.orderItemId()).ship(line.qty());
        }

        // 채번에 쓴 시각을 shippedAt·미수 발생 시점에 그대로 — 표시 코드와 날짜가 어긋나지 않게
        StatementNumberAllocator.Issued issued = statementNumberAllocator.next(wholesalerId);
        outbound.ship(issued.issuedAt(), issued.statementNumber());
        appendReceivable(outbound, itemsById, lines, issued.issuedAt());

        em.flush();
        return outboundQueryService.detail(wholesalerId, outboundId);
    }

    /** 봉투의 살아있는 항목 — PACKED 포장은 배분취소가 막혀 있어(DOCUMENT_FINALIZED) 락 아래서 안정적이다. */
    private List<ShipLine> shipLines(Long outboundId) {
        List<ShipLine> lines = new ArrayList<>();
        jdbc.query("""
                select pk.order_id, pi.order_item_id, pi.qty
                from wholesale.packing pk
                join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
                where pk.outbound_id = :id
                order by pi.id
                """, new MapSqlParameterSource("id", outboundId), rs -> {
            lines.add(new ShipLine(rs.getLong("order_id"), rs.getLong("order_item_id"), rs.getInt("qty")));
        });
        return lines;
    }

    private record ShipLine(long orderId, long orderItemId, int qty) {
    }

    /**
     * 미수 원장 기록 [X-1] — 스키마상 모든 행이 주문을 가리키므로 봉투의 주문마다
     * OUTBOUND(+) 행 하나(금액 = 그 주문의 출고 품목 qty×단가 합, 주문 하나면 행도 하나).
     * balance_after 는 검산용 파생값이라 partner 행 락 아래서 잇는다 — 입금(정산 티켓)과
     * 같은 직렬화 지점이다.
     */
    private void appendReceivable(Outbound outbound, Map<Long, OrderItem> itemsById,
                                  List<ShipLine> lines, OffsetDateTime occurredAt) {
        Map<Long, Long> amountByOrder = new TreeMap<>();
        for (ShipLine line : lines) {
            long amount = (long) line.qty() * itemsById.get(line.orderItemId()).getUnitPrice();
            amountByOrder.merge(line.orderId(), amount, Long::sum);
        }
        Long partnerId = outbound.getPartnerId();
        jdbc.query("select id from wholesale.partner where id = :id for update",
                new MapSqlParameterSource("id", partnerId), rs -> {
        });
        long balance = jdbc.queryForObject("""
                select coalesce(sum(delta), 0) from wholesale.receivable_ledger
                where partner_id = :partnerId
                """, new MapSqlParameterSource("partnerId", partnerId), Long.class);
        for (Map.Entry<Long, Long> entry : amountByOrder.entrySet()) {
            balance += entry.getValue();
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .partnerId(partnerId)
                    .requestId("OUTBOUND-" + outbound.getId() + "-" + entry.getKey())
                    .entryType(ReceivableEntryType.OUTBOUND)
                    .delta(entry.getValue())
                    .balanceAfter(balance)
                    .orderId(entry.getKey())
                    .outboundId(outbound.getId())
                    .occurredAt(occurredAt)
                    .build());
        }
    }
}
