package com.ondo.retail.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답. data 옆에 meta 가 붙는다.
 *
 * <pre>{ "data": [...], "meta": { page, size, totalElements, totalPages, hasNext } }</pre>
 *
 * @param data 이 장에 담긴 것들. 없으면 빈 배열이다
 * @param meta 페이지 정보
 */
public record PageResponse<T>(List<T> data, Meta meta) {

    /**
     * @param page          0-base. 첫 장이 0 이다
     * @param size          한 장에 몇 개
     * @param totalElements 전체 개수
     * @param totalPages    전체 장 수
     * @param hasNext       다음 장이 있는지. 무한 스크롤은 이것만 보면 된다
     */

    public record Meta(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new Meta(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.hasNext()));
    }
}
