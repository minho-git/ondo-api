package com.ondo.retail.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답. data 옆에 meta 가 붙는다.
 *
 * <pre>{ "data": [...], "meta": { page, size, totalElements, totalPages, hasNext } }</pre>
 */
public record PageResponse<T>(List<T> data, Meta meta) {

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
