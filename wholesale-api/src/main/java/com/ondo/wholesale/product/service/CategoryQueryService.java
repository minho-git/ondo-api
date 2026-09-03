package com.ondo.wholesale.product.service;

import com.ondo.wholesale.product.domain.Category;
import com.ondo.wholesale.product.dto.CategoryNodeResponse;
import com.ondo.wholesale.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카테고리 트리 조회 (MUL-90). 고정 마스터 소량이라 전건 로드 후 메모리에서 조립한다 —
 * self join 재귀 쿼리보다 단순하고, 행이 수십 개 수준이라 비용 문제가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryNodeResponse> tree() {
        // 쿼리가 sort_order, id 로 전역 정렬해 주므로 groupingBy(encounter order 보존)만 하면 된다
        List<Category> all = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, List<Category>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> toNode(c, byParent))
                .toList();
    }

    private CategoryNodeResponse toNode(Category category, Map<Long, List<Category>> byParent) {
        List<CategoryNodeResponse> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .map(c -> toNode(c, byParent))
                .toList();
        return new CategoryNodeResponse(category.getId(), category.getName(), category.getDepth(), children);
    }
}
