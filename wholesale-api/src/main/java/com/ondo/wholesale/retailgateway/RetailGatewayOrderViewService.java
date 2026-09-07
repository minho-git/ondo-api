package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.order.service.OrderStatusRule;
import com.ondo.wholesale.retailgateway.dto.RetailOrderViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소매 주문 조회 조립 (MUL-98).
 *
 * <p>평평한 두 결과(주문 · 라인)를 화면 모양으로 접고, <b>표시 상태를 여기서 만든다.</b>
 *
 * <p>상태를 도매가 만드는 게 핵심이다. DB 는 {@code NEW}·{@code CONFIRMED}·{@code CANCELLED}
 * 셋만 저장하고 출고 진행도를 합쳐 다섯이 된다. 그 규칙은 채빈의
 * {@link OrderStatusRule} 하나에만 두기로 했고 도매 화면도 그걸 쓴다 — 소매가 같은
 * 규칙을 또 짜면 두 화면이 같은 주문을 다르게 부른다. 라벨을 같이 내리는 이유도 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetailGatewayOrderViewService {

    private final RetailGatewayOrderViewQuery query;

    /**
     * 그 소매처의 주문서들에 딸린 도매처 주문 전부.
     *
     * <p>소매가 주문서 id 를 여럿 넘기고 우리는 평평하게 돌려준다. 주문서별로 묶는 건
     * 소매가 한다 — 소매만 자기 주문서를 알기 때문이다.
     */
    public List<RetailOrderViewResponse> orders(long retailerId, List<Long> retailOrderIds) {
        List<RetailGatewayOrderViewQuery.OrderRow> orders = query.orders(retailerId, retailOrderIds);
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RetailOrderViewResponse.Item>> itemsByOrder = new LinkedHashMap<>();
        Map<Long, int[]> sumsByOrder = new LinkedHashMap<>();   // [주문수량 합, 배분 합, 출고 합]

        for (RetailGatewayOrderViewQuery.ItemRow row : query.items(orders.stream()
                .map(RetailGatewayOrderViewQuery.OrderRow::orderId).toList())) {
            itemsByOrder.computeIfAbsent(row.orderId(), k -> new ArrayList<>()).add(row.item());
            int[] sums = sumsByOrder.computeIfAbsent(row.orderId(), k -> new int[3]);
            sums[0] += row.item().qty();
            sums[1] += row.allocatedQty();
            sums[2] += row.shippedQty();
        }

        List<RetailOrderViewResponse> result = new ArrayList<>();
        for (RetailGatewayOrderViewQuery.OrderRow o : orders) {
            List<RetailOrderViewResponse.Item> items = itemsByOrder.getOrDefault(o.orderId(), List.of());
            int[] sums = sumsByOrder.getOrDefault(o.orderId(), new int[3]);

            OrderStatusRule.Derived derived = OrderStatusRule.derive(o.status(), sums[0], sums[1], sums[2]);

            result.add(new RetailOrderViewResponse(
                    o.orderId(), o.retailOrderId(), o.orderNumber(), o.orderedAt(), o.wholesaler(),
                    derived.key().name(), derived.label(),
                    o.paymentTerm(), o.receiveMethod(), o.agentName(), o.agentPhone(),
                    amount(items), derived.cancellable(), items));
        }
        return result;
    }

    /** 주문 시점 스냅샷으로 센다. 지금 판매가가 바뀌어도 청구액은 그때 값이다. */
    private static int amount(List<RetailOrderViewResponse.Item> items) {
        return items.stream().mapToInt(i -> i.qty() * i.unitPrice()).sum();
    }
}
