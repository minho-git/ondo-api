package com.ondo.wholesale.settlement.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 연체 계산 (MUL-129) — 출고마다 기한을 따로 센다.
 *
 * <pre>
 * 기한 = 출고일(KST) + 외상기간      기한 당일은 연체가 아니다
 * 주문에 붙은 돈은 그 주문의 오래된 출고부터 갚은 것으로 친다
 * 기한이 지났는데 남은 돈이 있으면 연체
 * </pre>
 *
 * <p>잔액 전체를 하나로 보지 않는 건 "언제부터 밀렸나"(최장 일수)를 말해야 해서다.
 *
 * <p>외상기간은 거래처 설정(MUL-128) 전까지 <b>모든 주문 {@value #DEFAULT_CREDIT_DAYS}일</b>이다
 * (2026-09-17 팀 결정). 128 에서 주문별 값으로 바꾸는 자리가 여기 하나다.
 */
public final class OverdueCalculator {

    public static final int DEFAULT_CREDIT_DAYS = 1;

    private OverdueCalculator() {
    }

    /** 출고로 생긴 미수 한 건. */
    public record Shipment(long orderId, LocalDate shippedOn, long amount, long sequence) {}

    /** @param count 연체가 걸린 주문 수 · @param maxDays 가장 오래 밀린 것의 D+n (없으면 0) */
    public record Overdue(long amount, int count, int maxDays) {

        public static final Overdue NONE = new Overdue(0, 0, 0);
    }

    /**
     * @param shipments   한 거래처의 출고들
     * @param paidByOrder 주문별 붙은 돈(취소 배분 · 무효 입금 제외)
     */
    public static Overdue of(List<Shipment> shipments, Map<Long, Long> paidByOrder, LocalDate today) {
        Map<Long, List<Shipment>> byOrder = new HashMap<>();
        for (Shipment shipment : shipments) {
            byOrder.computeIfAbsent(shipment.orderId(), k -> new ArrayList<>()).add(shipment);
        }

        long amount = 0;
        int count = 0;
        int maxDays = 0;
        for (Map.Entry<Long, List<Shipment>> entry : byOrder.entrySet()) {
            long paid = paidByOrder.getOrDefault(entry.getKey(), 0L);
            boolean orderOverdue = false;
            List<Shipment> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(Shipment::shippedOn).thenComparing(Shipment::sequence))
                    .toList();
            for (Shipment shipment : ordered) {
                long covered = Math.min(paid, shipment.amount());
                paid -= covered;
                long remaining = shipment.amount() - covered;
                if (remaining <= 0) {
                    continue;
                }
                LocalDate due = shipment.shippedOn().plusDays(DEFAULT_CREDIT_DAYS);
                long days = ChronoUnit.DAYS.between(due, today);
                if (days > 0) {
                    amount += remaining;
                    maxDays = (int) Math.max(maxDays, days);
                    orderOverdue = true;
                }
            }
            if (orderOverdue) {
                count++;
            }
        }
        return new Overdue(amount, count, maxDays);
    }
}
