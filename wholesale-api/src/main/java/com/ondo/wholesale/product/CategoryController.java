package com.ondo.wholesale.product;

import com.ondo.wholesale.product.dto.CategoryNodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 카테고리 트리 조회 (MUL-90) — 계약 원본: api-lite/02_상품게시/GET_categories.md. */
@Tag(name = "02 상품·게시")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @Operation(summary = "카테고리 트리", description = """
            depth 1~3 전체를 한 번에 내린다. 고정 마스터라 프론트 캐시 가능.
            상품 지정은 `depth: 3` 리프만(위반 시 400 `CATEGORY_NOT_LEAF`),
            목록 필터의 `categoryId`는 반대로 상위 노드를 받는다.""")
    @GetMapping("/api/wholesale/categories")
    public List<CategoryNodeResponse> categories() {
        return categoryQueryService.tree();
    }
}
