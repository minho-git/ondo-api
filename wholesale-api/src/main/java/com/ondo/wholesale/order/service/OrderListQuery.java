package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.SettlementStatus;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 주문 목록 쿼리 파라미터 — 형식 검증과 정렬 파싱을 HTTP 계층에서 끝낸 값 (MUL-47).
 *
 * <p>filter 는 컨트롤러 시그니처가 {@link OrderFilterKey} 타입이라 여기서 파싱하지 않는다 —
 * 미정의 값 400 은 타입 미스매치 핸들러가 낸다. settlementStatus 는 계약이 문자열이라
 * 여기서 {@link SettlementStatus} 로 파싱하고 미정의 값을 400 으로 거른다.
 */
public record OrderListQuery(OrderFilterKey filter, String q, Long retailerId,
                             SettlementStatus settlementStatus, LocalDate from, LocalDate to,
                             int page, int size, Sort sort) {

    /** 정렬 화이트리스트 — 계약에 없는 키는 400. */
    private static final Map<String, String> SORT_KEYS = Map.of(
            "orderedAt", "orderedAt",
            "orderNumber", "orderNumber");

    private static final int MAX_PAGE_SIZE = 100;

    public static OrderListQuery of(OrderFilterKey filter, String q, Long retailerId,
                                    String settlementStatus, LocalDate from, LocalDate to,
                                    int page, int size, String sort) {
        if (size > MAX_PAGE_SIZE) {
            throw validationFailed("size", "size 는 최대 " + MAX_PAGE_SIZE + " 이다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw validationFailed("from", "from 이 to 보다 뒤일 수 없다.");
        }
        return new OrderListQuery(filter, q, retailerId, parseSettlementStatus(settlementStatus),
                from, to, page, size, parseSort(sort));
    }

    private static SettlementStatus parseSettlementStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SettlementStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw validationFailed("settlementStatus", "정의되지 않은 정산 상태: " + raw);
        }
    }

    /** "필드,방향" 또는 "필드". 페이지 경계 안정화를 위해 id 를 타이브레이커로 붙인다. */
    private static Sort parseSort(String sort) {
        String raw = (sort == null || sort.isBlank()) ? "orderedAt,desc" : sort;
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
