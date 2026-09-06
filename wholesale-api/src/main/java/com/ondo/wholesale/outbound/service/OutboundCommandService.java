package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.domain.Order;
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
import com.ondo.wholesale.outbound.repository.OutboundRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final OutboundNumberAllocator outboundNumberAllocator;
    private final PackingAssembler packingAssembler;
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
}
