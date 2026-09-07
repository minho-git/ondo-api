package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.time.KstDays;
import com.ondo.wholesale.order.domain.OrderItem;
import com.ondo.wholesale.order.domain.PackingItem;
import com.ondo.wholesale.order.domain.Partner;
import com.ondo.wholesale.outbound.OutboundStatusFilter;
import com.ondo.wholesale.outbound.domain.Outbound;
import com.ondo.wholesale.product.domain.Variant;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 출고 목록 검색 조건. 조합은 {@link OutboundQueryService}가 한다 (MUL-49). */
final class OutboundSpecs {

    private OutboundSpecs() {
    }

    static Specification<Outbound> ownedBy(Long wholesalerId) {
        return (root, query, cb) -> cb.equal(root.get("wholesalerId"), wholesalerId);
    }

    /** 저장 상태가 아니라 shippedAt 의 null 여부로 가른다 (D-074). */
    static Specification<Outbound> status(OutboundStatusFilter status) {
        return (root, query, cb) -> (status == OutboundStatusFilter.SHIPPED)
                ? cb.isNotNull(root.get("shippedAt"))
                : cb.isNull(root.get("shippedAt"));
    }

    /** 정산 화면과 같은 축 — partner 를 거쳐 소매 id 로 스코핑한다. */
    static Specification<Outbound> retailerScoped(Long retailerId) {
        return (root, query, cb) -> {
            Subquery<Long> partner = query.subquery(Long.class);
            Root<Partner> p = partner.from(Partner.class);
            partner.select(cb.literal(1L)).where(
                    cb.equal(p.get("id"), root.get("partnerId")),
                    cb.equal(p.get("retailerId"), retailerId));
            return cb.exists(partner);
        };
    }

    /** q 는 봉투에 담긴 상품명만 거른다 — 소매처명은 외부 시스템 값이라 검색 대상이 아니다. */
    static Specification<Outbound> searchProduct(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> {
            Subquery<Long> item = query.subquery(Long.class);
            Root<PackingItem> pi = item.from(PackingItem.class);
            Root<OrderItem> oi = item.from(OrderItem.class);
            Root<Variant> v = item.from(Variant.class);
            item.select(cb.literal(1L)).where(
                    cb.equal(pi.get("packing").get("outboundId"), root.get("id")),
                    cb.isNull(pi.get("deletedAt")),
                    cb.equal(oi.get("id"), pi.get("orderItemId")),
                    cb.equal(v.get("id"), oi.get("variantId")),
                    cb.like(cb.lower(v.get("product").get("name")), pattern));
            return cb.exists(item);
        };
    }

    /**
     * 기간 필터 — KST 로 [from 00:00, to 다음날 00:00). 축은 상태 탭을 따른다:
     * SHIPPED 탭은 출고 일시(shippedAt), 그 외에는 포장 일시(createdAt).
     */
    static Specification<Outbound> periodBetween(OutboundStatusFilter status,
                                                 LocalDate from, LocalDate to) {
        String axis = (status == OutboundStatusFilter.SHIPPED) ? "shippedAt" : "createdAt";
        return (root, query, cb) -> {
            var path = root.<OffsetDateTime>get(axis);
            if (from != null && to != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(path, KstDays.start(from)),
                        cb.lessThan(path, KstDays.startOfNext(to)));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(path, KstDays.start(from));
            }
            return cb.lessThan(path, KstDays.startOfNext(to));
        };
    }
}
