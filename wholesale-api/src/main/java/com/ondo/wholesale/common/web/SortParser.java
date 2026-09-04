package com.ondo.wholesale.common.web;

import com.ondo.wholesale.common.error.ApiException;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * 목록 API 공용 정렬 파싱 — "필드,방향" 또는 "필드".
 *
 * <p>화이트리스트에 없는 키·모르는 방향은 400 이다. 페이지 경계 안정화를 위해
 * id 내림차순을 타이브레이커로 항상 붙인다.
 */
public final class SortParser {

    private SortParser() {
    }

    public static Sort parse(String sort, String defaultSort, Map<String, String> sortKeys) {
        String raw = (sort == null || sort.isBlank()) ? defaultSort : sort;
        String[] parts = raw.split(",", 2);
        String property = sortKeys.get(parts[0].trim());
        Sort.Direction direction = (parts.length < 2) ? Sort.Direction.ASC
                : "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC
                : "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC
                : null;
        if (property == null || direction == null) {
            throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
        }
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
