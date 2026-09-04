package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.domain.Order;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.OrderStatus;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.product.domain.Variant;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** 주문 목록 검색 조건. 조합은 {@link OrderQueryService}가 한다 (MUL-47). */
final class OrderSpecs {

    /** 기간 필터의 하루 경계 기준 시간대 — 화면·계약이 한국 날짜를 전제한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private OrderSpecs() {
    }

    static Specification<Order> ownedBy(Long wholesalerId) {
        return (root, query, cb) -> cb.equal(root.get("wholesalerId"), wholesalerId);
    }

    static Specification<Order> idIn(List<Long> ids) {
        return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }

    /** 검색어는 거래처 상호와 라인 상품명 양쪽에 부분 일치(대소문자 무시)로 건다. */
    static Specification<Order> searchLike(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> {
            Subquery<Long> partner = query.subquery(Long.class);
            Root<Partner> p = partner.from(Partner.class);
            partner.select(cb.literal(1L)).where(
                    cb.equal(p.get("id"), root.get("partnerId")),
                    cb.like(cb.lower(p.get("retailerName")), pattern));

            Subquery<Long> item = query.subquery(Long.class);
            Root<OrderItem> oi = item.from(OrderItem.class);
            Root<Variant> v = item.from(Variant.class);
            item.select(cb.literal(1L)).where(
                    cb.equal(oi.get("order"), root),
                    cb.equal(v.get("id"), oi.get("variantId")),
                    cb.like(cb.lower(v.get("product").get("name")), pattern));

            return cb.or(cb.exists(partner), cb.exists(item));
        };
    }

    /** 정산 탭 전용 — 그 거래처의 확정 주문만 내려간다는 계약을 조건으로 옮긴 것. */
    static Specification<Order> retailerScoped(Long retailerId) {
        return (root, query, cb) -> {
            Subquery<Long> partner = query.subquery(Long.class);
            Root<Partner> p = partner.from(Partner.class);
            partner.select(cb.literal(1L)).where(
                    cb.equal(p.get("id"), root.get("partnerId")),
                    cb.equal(p.get("retailerId"), retailerId));
            return cb.and(cb.exists(partner),
                    cb.equal(root.get("status"), OrderStatus.CONFIRMED));
        };
    }

    /**
     * 표시 상태 칩의 필터. NEW·CANCELLED 는 저장값 비교고, 확정 계열 3값은
     * 출고 합계 서브쿼리로 가른다 — 파생 규칙은 {@link OrderStatuses}와 같은 어휘다.
     */
    static Specification<Order> derivedStatus(OrderFilterKey filter) {
        return (root, query, cb) -> switch (filter) {
            case ALL -> cb.conjunction();
            case NEW -> cb.equal(root.get("status"), OrderStatus.NEW);
            case CANCELLED -> cb.equal(root.get("status"), OrderStatus.CANCELLED);
            case CONFIRMED -> cb.and(
                    cb.equal(root.get("status"), OrderStatus.CONFIRMED),
                    cb.equal(shippedSum(root, query, cb), 0));
            case PARTIALLY_SHIPPED -> cb.and(
                    cb.equal(root.get("status"), OrderStatus.CONFIRMED),
                    cb.greaterThan(shippedSum(root, query, cb), 0),
                    cb.lessThan(shippedSum(root, query, cb), totalSum(root, query, cb)));
            case SHIPPED -> cb.and(
                    cb.equal(root.get("status"), OrderStatus.CONFIRMED),
                    cb.greaterThan(shippedSum(root, query, cb), 0),
                    cb.equal(shippedSum(root, query, cb), totalSum(root, query, cb)));
        };
    }

    /** 주문일 기간 — KST 로 [from 00:00, to 다음날 00:00). */
    static Specification<Order> orderedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var path = root.<OffsetDateTime>get("orderedAt");
            if (from != null && to != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(path, from.atStartOfDay(KST).toOffsetDateTime()),
                        cb.lessThan(path, to.plusDays(1).atStartOfDay(KST).toOffsetDateTime()));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(path, from.atStartOfDay(KST).toOffsetDateTime());
            }
            return cb.lessThan(path, to.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
        };
    }

    private static Subquery<Integer> shippedSum(Root<Order> root, CriteriaQuery<?> query,
                                                CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<OrderItem> oi = sub.from(OrderItem.class);
        sub.select(cb.coalesce(cb.sum(oi.get("shippedQty")), 0))
                .where(cb.equal(oi.get("order"), root));
        return sub;
    }

    private static Subquery<Integer> totalSum(Root<Order> root, CriteriaQuery<?> query,
                                              CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<OrderItem> oi = sub.from(OrderItem.class);
        sub.select(cb.coalesce(cb.sum(oi.get("qty")), 0))
                .where(cb.equal(oi.get("order"), root));
        return sub;
    }
}
