package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.dto.request.ColorOptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * colorOptions 전체 교체 diff 판단의 단위 검증 (MUL-93 2단계) —
 * 추가·삭제 후보 계산, seq 발급, 옵션 행 재사용, 일괄 검사 실패 시 무변경.
 * 검증기·검사기는 목이고 애그리거트는 실제 엔티티로 조립한다.
 */
class ColorOptionDifferTest {

    private final ProductOptionValidator validator = mock(ProductOptionValidator.class);
    private final VariantUsageChecker checker = mock(VariantUsageChecker.class);
    private final ColorOptionDiffer differ = new ColorOptionDiffer(validator, checker);

    private final Color 블랙 = 색(1L);
    private final Color 베이지 = 색(7L);
    private Product product;

    @BeforeEach
    void 블랙_S_M_상품을_만든다() {
        product = Product.builder()
                .wholesalerId(10L).productNumber(1).name("diff 상품").categoryId(121L).build();
        var option = product.addColorOption(블랙);
        option.addVariant(Size.S, product.nextVariantSeq());
        option.addVariant(Size.M, product.nextVariantSeq());
        when(validator.validate(anyList())).thenReturn(Map.of(1L, 블랙, 7L, 베이지));
    }

    @Test
    void 새_색을_추가하면_옵션과_variant가_생기고_seq가_이어진다() {
        differ.replace(product, List.of(
                new ColorOptionRequest(1L, List.of(Size.S, Size.M)),
                new ColorOptionRequest(7L, List.of(Size.S))));

        assertThat(product.getColorOptions()).hasSize(2);
        Variant 신규 = product.getColorOptions().get(1).getVariants().get(0);
        assertThat(신규.getVariantSeq()).isEqualTo(3); // 기존 1·2 다음
    }

    @Test
    void 요청에_없는_색의_variant는_지워지고_옵션_행은_남는다() {
        differ.replace(product, List.of(new ColorOptionRequest(7L, List.of(Size.S))));

        assertThat(product.getColorOptions()).hasSize(2); // 블랙 옵션 행 유지
        assertThat(product.getColorOptions().get(0).getVariants())
                .allMatch(v -> !v.isAlive()); // 블랙 S·M 전부 soft delete
    }

    @Test
    void 사이즈만_빼면_그_variant만_지운다() {
        differ.replace(product, List.of(new ColorOptionRequest(1L, List.of(Size.S))));

        var variants = product.getColorOptions().get(0).getVariants();
        assertThat(variants.stream().filter(Variant::isAlive))
                .extracting(Variant::getSize).containsExactly(Size.S);
    }

    @Test
    void 뺐던_색을_다시_추가하면_옵션_행을_재사용한다() {
        differ.replace(product, List.of(new ColorOptionRequest(7L, List.of(Size.S)))); // 블랙 제거
        differ.replace(product, List.of(
                new ColorOptionRequest(1L, List.of(Size.XL)),
                new ColorOptionRequest(7L, List.of(Size.S)))); // 블랙 재추가

        assertThat(product.getColorOptions()).hasSize(2); // 새 옵션 행이 안 생겼다
        assertThat(product.getColorOptions().get(0).getVariants().stream().filter(Variant::isAlive))
                .extracting(Variant::getSize).containsExactly(Size.XL);
    }

    @Test
    void 일괄_검사에서_걸리면_아무것도_안_지운다() {
        doThrow(new ApiException(ErrorCode.VARIANT_HAS_STOCK)).when(checker).ensureDeletable(any());

        assertThatThrownBy(() -> differ.replace(product, List.of(new ColorOptionRequest(7L, List.of(Size.S)))))
                .isInstanceOf(ApiException.class);

        assertThat(product.getColorOptions().get(0).getVariants())
                .allMatch(Variant::isAlive); // 블랙 S·M 이 그대로 살아 있다
    }

    private Color 색(Long id) {
        Color color = mock(Color.class);
        when(color.getId()).thenReturn(id);
        return color;
    }
}
