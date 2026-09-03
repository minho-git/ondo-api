package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.product.domain.Category;
import com.ondo.wholesale.product.dto.CategoryPathItem;
import com.ondo.wholesale.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 카테고리 마스터 위의 도메인 규칙 한 곳 — 리프 검증(D-058)·경로 조립·하위 포함 검색.
 * 마스터는 소량 고정이라 호출마다 전건 로드한다 (트리 캐시 없음).
 */
@Component
@RequiredArgsConstructor
public class CategoryTree {

    private final CategoryRepository categoryRepository;

    /** 상품에 달 수 있는 리프(depth 3)인지 검증하고 그 카테고리를 돌려준다. */
    public Category requireLeaf(Long categoryId) {
        Map<Long, Category> byId = load();
        Category category = byId.get(categoryId);
        if (category == null) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (category.getDepth() != 3) {
            throw new ApiException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        return category;
    }

    /** 리프 id → 루트부터 리프까지의 경로 (항상 3단). */
    public List<CategoryPathItem> pathOf(Long leafId) {
        Map<Long, Category> byId = load();
        Deque<CategoryPathItem> path = new ArrayDeque<>();
        for (Category cursor = byId.get(leafId); cursor != null; cursor = byId.get(cursor.getParentId())) {
            path.addFirst(new CategoryPathItem(cursor.getId(), cursor.getName()));
            if (cursor.getParentId() == null) {
                break;
            }
        }
        return List.copyOf(path);
    }


    private Map<Long, Category> load() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }
}
