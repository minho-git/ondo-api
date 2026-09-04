package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.web.SortParser;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Map;

/**
 * 상품 목록 쿼리 파라미터 — 형식 검증과 정렬 파싱을 HTTP 계층에서 끝낸 값.
 * 여기서 400 을 다 걸러내므로 서비스는 정책(검색·집계)만 다룬다.
 */
public record ProductListQuery(String q, Long categoryId, LocalDate from, LocalDate to,
                               int page, int size, Sort sort) {

    /** 정렬 화이트리스트 — 계약에 없는 키는 400. */
    private static final Map<String, String> SORT_KEYS = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "productNumber", "productNumber");

    private static final int MAX_PAGE_SIZE = 100;

    public static ProductListQuery of(String q, Long categoryId, LocalDate from, LocalDate to,
                                      int page, int size, String sort) {
        if (size > MAX_PAGE_SIZE) {
            throw ApiException.validationFailed("size", "size 는 최대 " + MAX_PAGE_SIZE + " 이다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validationFailed("from", "from 이 to 보다 뒤일 수 없다.");
        }
        return new ProductListQuery(q, categoryId, from, to, page, size,
                SortParser.parse(sort, "createdAt,desc", SORT_KEYS));
    }
}
