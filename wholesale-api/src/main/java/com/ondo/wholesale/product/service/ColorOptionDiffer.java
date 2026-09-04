package com.ondo.wholesale.product.service;

import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.product.domain.ColorOption;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.dto.request.ColorOptionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * colorOptions 전체 교체 diff (MUL-93 2단계). 요청 = 반영 후 전체 상태다.
 *
 * <p>색 추가 → 옵션 신규(단, 뺐던 색은 남은 옵션 행 재사용 — color_option_uk).
 * 사이즈 추가 → 새 variant 행(soft delete 재추가 포함, seq 새로 발급).
 * 색·사이즈 제거 → variant soft delete 만 (옵션 행·가격 행은 D-053 으로 유지).
 * 삭제 후보를 전부 모은 뒤 {@link VariantUsageChecker#ensureDeletable} 일괄 검사를
 * 통과했을 때만 지운다 — 일부만 걸려도 아무것도 안 지워진다.
 */
@Component
@RequiredArgsConstructor
public class ColorOptionDiffer {

    private final ProductOptionValidator productOptionValidator;
    private final VariantUsageChecker variantUsageChecker;

    public void replace(Product product, List<ColorOptionRequest> requested) {
        Map<Long, Color> colors = productOptionValidator.validate(requested);

        Map<Long, ColorOption> existingByColorId = new HashMap<>();
        product.getColorOptions().forEach(option -> existingByColorId.put(option.getColor().getId(), option));

        List<Variant> deleteCandidates = new ArrayList<>();
        Set<Long> requestedColorIds = new HashSet<>();
        for (ColorOptionRequest optionRequest : requested) {
            requestedColorIds.add(optionRequest.colorId());
            ColorOption option = existingByColorId.get(optionRequest.colorId());
            if (option == null) {
                option = product.addColorOption(colors.get(optionRequest.colorId()));
            }
            Map<Size, Variant> aliveBySize = new HashMap<>();
            option.getVariants().stream().filter(Variant::isAlive)
                    .forEach(v -> aliveBySize.put(v.getSize(), v));
            for (Size size : optionRequest.sizes()) {
                if (!aliveBySize.containsKey(size)) {
                    option.addVariant(size, product.nextVariantSeq());
                }
            }
            aliveBySize.forEach((size, variant) -> {
                if (!optionRequest.sizes().contains(size)) {
                    deleteCandidates.add(variant);
                }
            });
        }
        existingByColorId.forEach((colorId, option) -> {
            if (!requestedColorIds.contains(colorId)) {
                option.getVariants().stream().filter(Variant::isAlive).forEach(deleteCandidates::add);
            }
        });

        if (!deleteCandidates.isEmpty()) {
            variantUsageChecker.ensureDeletable(deleteCandidates.stream().map(Variant::getId).toList());
            deleteCandidates.forEach(Variant::softDelete);
        }
    }
}
