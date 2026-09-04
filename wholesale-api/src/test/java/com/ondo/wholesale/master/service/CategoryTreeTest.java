package com.ondo.wholesale.master.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.master.domain.Category;
import com.ondo.wholesale.master.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 카테고리 트리 규칙의 단위 검증 — 리프 판정(D-058)·경로 조립·하위 포함 검색.
 * 마스터 엔티티는 읽기 전용(생성자 없음)이라 목으로 만든다.
 */
class CategoryTreeTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final CategoryTree categoryTree = new CategoryTree(categoryRepository);

    @BeforeEach
    void 여성_아우터_코트_트리를_심는다() {
        // 여성(1) > 아우터(11) > 코트(111)·재킷(112), 여성(1) > 상의(12) > 티셔츠(121)
        // 목 생성(내부 스터빙)을 먼저 끝내고 리포지토리를 스터빙한다 — when() 중첩 금지
        List<Category> tree = List.of(
                카테고리(1L, null, (short) 1, "여성"),
                카테고리(11L, 1L, (short) 2, "아우터"),
                카테고리(12L, 1L, (short) 2, "상의"),
                카테고리(111L, 11L, (short) 3, "코트"),
                카테고리(112L, 11L, (short) 3, "재킷"),
                카테고리(121L, 12L, (short) 3, "티셔츠"));
        when(categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(tree);
    }

    @Test
    void 리프면_그_카테고리를_돌려준다() {
        assertThat(categoryTree.requireLeaf(111L).getName()).isEqualTo("코트");
    }

    @Test
    void 없는_카테고리는_CATEGORY_NOT_FOUND다() {
        assertThatThrownBy(() -> categoryTree.requireLeaf(999L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Test
    void 리프가_아니면_CATEGORY_NOT_LEAF다() {
        assertThatThrownBy(() -> categoryTree.requireLeaf(11L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_LEAF));
    }

    @Test
    void 경로는_뿌리부터_리프까지_순서대로다() {
        assertThat(categoryTree.pathOf(111L))
                .extracting(item -> item.name())
                .containsExactly("여성", "아우터", "코트");
    }

    @Test
    void 하위_검색은_자기와_모든_하위를_담는다() {
        assertThat(categoryTree.subtreeIds(1L))
                .containsExactlyInAnyOrder(1L, 11L, 12L, 111L, 112L, 121L);
        assertThat(categoryTree.subtreeIds(11L))
                .containsExactlyInAnyOrder(11L, 111L, 112L);
        assertThat(categoryTree.subtreeIds(111L))
                .containsExactly(111L); // 리프는 자기 자신뿐
    }

    private Category 카테고리(Long id, Long parentId, short depth, String name) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getDepth()).thenReturn(depth);
        when(category.getName()).thenReturn(name);
        return category;
    }
}
