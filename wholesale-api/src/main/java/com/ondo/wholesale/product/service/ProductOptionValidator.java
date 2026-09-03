package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.product.domain.Color;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.dto.ColorOptionRequest;
import com.ondo.wholesale.product.repository.ColorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 색상 옵션 구성의 정책 검증 (등록·수정 공용).
 * 형식 검증(Bean Validation)을 통과한 요청이 여기로 온다.
 */
@Component
@RequiredArgsConstructor
public class ProductOptionValidator {

    private final ColorRepository colorRepository;

    /** 구성 규칙을 검증하고, 응답 조립에 필요한 색상 엔티티 맵(colorId → Color)을 돌려준다. */
    public Map<Long, Color> validate(List<ColorOptionRequest> colorOptions) {
        if (colorOptions.isEmpty()) {
            throw new ApiException(ErrorCode.OPTION_REQUIRED);
        }
        Set<Long> seenColors = new HashSet<>();
        for (ColorOptionRequest option : colorOptions) {
            if (!seenColors.add(option.colorId())) {
                throw new ApiException(ErrorCode.COLOR_DUPLICATED);
            }
            if (option.sizes().isEmpty()) {
                throw new ApiException(ErrorCode.OPTION_REQUIRED);
            }
            Set<Size> seenSizes = new HashSet<>();
            for (Size size : option.sizes()) {
                if (!seenSizes.add(size)) {
                    throw new ApiException(ErrorCode.SIZE_DUPLICATED);
                }
            }
        }

        Map<Long, Color> colors = colorRepository.findAllById(seenColors).stream()
                .collect(Collectors.toMap(Color::getId, Function.identity()));
        List<ErrorResponse.FieldError> unknown = seenColors.stream()
                .filter(id -> !colors.containsKey(id))
                .map(id -> new ErrorResponse.FieldError("colorOptions.colorId", "존재하지 않는 색상: " + id))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), unknown);
        }
        return colors;
    }
}
