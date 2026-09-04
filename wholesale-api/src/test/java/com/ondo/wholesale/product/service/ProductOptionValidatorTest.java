package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.master.repository.ColorRepository;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.dto.request.ColorOptionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 색상 옵션 구성 규칙의 단위 검증 (MUL-91) — 중복·빈 목록 판정과 색 존재 확인. */
class ProductOptionValidatorTest {

    private final ColorRepository colorRepository = mock(ColorRepository.class);
    private final ProductOptionValidator validator = new ProductOptionValidator(colorRepository);

    @Test
    void 정상이면_색_id로_찾을_수_있는_맵을_돌려준다() {
        색이_있다(1L, 7L);

        var colors = validator.validate(List.of(
                new ColorOptionRequest(1L, List.of(Size.S, Size.M)),
                new ColorOptionRequest(7L, List.of(Size.S))));

        assertThat(colors).containsOnlyKeys(1L, 7L);
    }

    @Test
    void 빈_목록이면_OPTION_REQUIRED다() {
        걸린다(List.of(), ErrorCode.OPTION_REQUIRED);
    }

    @Test
    void 사이즈가_빈_색이_있으면_OPTION_REQUIRED다() {
        걸린다(List.of(new ColorOptionRequest(1L, List.of())), ErrorCode.OPTION_REQUIRED);
    }

    @Test
    void 색이_중복이면_COLOR_DUPLICATED다() {
        걸린다(List.of(
                new ColorOptionRequest(1L, List.of(Size.S)),
                new ColorOptionRequest(1L, List.of(Size.M))), ErrorCode.COLOR_DUPLICATED);
    }

    @Test
    void 한_색에_사이즈가_중복이면_SIZE_DUPLICATED다() {
        걸린다(List.of(new ColorOptionRequest(1L, List.of(Size.S, Size.S))), ErrorCode.SIZE_DUPLICATED);
    }

    @Test
    void 없는_색이면_VALIDATION_FAILED에_필드가_담긴다() {
        색이_있다(1L); // 7 은 없음

        assertThatThrownBy(() -> validator.validate(List.of(
                new ColorOptionRequest(1L, List.of(Size.S)),
                new ColorOptionRequest(7L, List.of(Size.S)))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).hasSize(1);
                    assertThat(ex.errors().get(0).reason()).contains("7");
                });
    }

    private void 색이_있다(Long... ids) {
        List<Color> colors = java.util.Arrays.stream(ids).map(id -> {
            Color color = mock(Color.class);
            when(color.getId()).thenReturn(id);
            return color;
        }).toList();
        when(colorRepository.findAllById(any())).thenReturn(colors);
    }

    private void 걸린다(List<ColorOptionRequest> request, ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(expected));
    }
}
