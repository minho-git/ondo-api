package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
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
            throw validationFailed("size", "size 는 최대 " + MAX_PAGE_SIZE + " 이다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw validationFailed("from", "from 이 to 보다 뒤일 수 없다.");
        }
        return new ProductListQuery(q, categoryId, from, to, page, size, parseSort(sort));
    }

    /** "필드,방향" 또는 "필드". 페이지 경계 안정화를 위해 id 를 타이브레이커로 붙인다. */
    private static Sort parseSort(String sort) {
        String raw = (sort == null || sort.isBlank()) ? "createdAt,desc" : sort;
        String[] parts = raw.split(",", 2);
        String property = SORT_KEYS.get(parts[0].trim());
        Sort.Direction direction = (parts.length < 2) ? Sort.Direction.ASC
                : "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC
                : "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC
                : null;
        if (property == null || direction == null) {
            throw validationFailed("sort", "지원하지 않는 정렬: " + raw);
        }
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private static ApiException validationFailed(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new ErrorResponse.FieldError(field, reason)));
    }
}
