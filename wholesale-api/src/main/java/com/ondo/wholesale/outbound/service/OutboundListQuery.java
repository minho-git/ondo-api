package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.web.SortParser;
import com.ondo.wholesale.outbound.OutboundStatusFilter;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Map;

/**
 * 출고 목록 쿼리 파라미터 — 형식 검증과 정렬 파싱을 HTTP 계층에서 끝낸 값 (MUL-49).
 *
 * <p>status 는 컨트롤러 시그니처가 {@link OutboundStatusFilter} 타입이라 여기서 파싱하지
 * 않는다 — 미정의 값 400 은 타입 미스매치 핸들러가 낸다 (OrderListQuery 전례).
 * 기간 필터의 축은 status 가 SHIPPED 면 shippedAt, 그 외에는 createdAt 이다 —
 * 축 적용은 조회 쪽({@code OutboundSpecs}·{@code OutboundReader})이 한다.
 */
public record OutboundListQuery(OutboundStatusFilter status, String q, Long retailerId,
                                LocalDate from, LocalDate to, int page, int size, Sort sort) {

    /** 정렬 화이트리스트 — 계약에 없는 키는 400. */
    private static final Map<String, String> SORT_KEYS = Map.of(
            "createdAt", "createdAt",
            "outboundNumber", "outboundNumber");

    private static final int MAX_PAGE_SIZE = 100;

    public static OutboundListQuery of(OutboundStatusFilter status, String q, Long retailerId,
                                       LocalDate from, LocalDate to, int page, int size, String sort) {
        if (size > MAX_PAGE_SIZE) {
            throw ApiException.validationFailed("size", "size 는 최대 " + MAX_PAGE_SIZE + " 이다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validationFailed("from", "from 이 to 보다 뒤일 수 없다.");
        }
        return new OutboundListQuery(status, q, retailerId, from, to, page, size,
                SortParser.parse(sort, "createdAt,desc", SORT_KEYS));
    }
}
