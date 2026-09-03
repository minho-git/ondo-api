package com.ondo.wholesale.product.service;

import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.Product;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** 상품 목록 검색 조건. 조합은 {@link ProductQueryService}가 한다. */
final class ProductSpecs {

    /** 기간 필터의 하루 경계 기준 시간대 — 화면·계약이 한국 날짜를 전제한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ProductSpecs() {
    }

    static Specification<Product> ownedBy(Long wholesalerId) {
        return (root, query, cb) -> cb.equal(root.get("wholesalerId"), wholesalerId);
    }

    static Specification<Product> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** 검색어는 품명과 살아있는 게시글 제목 양쪽에 부분 일치(대소문자 무시)로 건다. */
    static Specification<Product> nameLike(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Listing> listing = sub.from(Listing.class);
            sub.select(cb.literal(1L)).where(
                    cb.equal(listing.get("product"), root),
                    cb.isNull(listing.get("deletedAt")),
                    cb.like(cb.lower(listing.get("title")), pattern));
            return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.exists(sub));
        };
    }

    static Specification<Product> categoryIn(List<Long> categoryIds) {
        return (root, query, cb) -> root.get("categoryId").in(categoryIds);
    }

    /** 등록일 기간 — KST 로 [from 00:00, to 다음날 00:00). */
    static Specification<Product> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var path = root.<OffsetDateTime>get("createdAt");
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
}
