package com.ondo.wholesale.product;

import com.ondo.wholesale.product.domain.Color;
import com.ondo.wholesale.product.dto.ColorGroupResponse;
import com.ondo.wholesale.product.repository.ColorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 색상 팔레트 조회 (MUL-90). 그룹 → 그룹 내 순서는 쿼리가 보장하고 여기선 그룹핑만 한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ColorQueryService {

    private final ColorRepository colorRepository;

    public List<ColorGroupResponse> palette() {
        List<ColorGroupResponse> groups = new ArrayList<>();
        List<ColorGroupResponse.ColorItem> items = null;
        Long currentGroupId = null;
        for (Color color : colorRepository.findAllWithGroupOrdered()) {
            if (!color.getGroup().getId().equals(currentGroupId)) {
                currentGroupId = color.getGroup().getId();
                items = new ArrayList<>();
                groups.add(new ColorGroupResponse(currentGroupId, color.getGroup().getName(), items));
            }
            items.add(new ColorGroupResponse.ColorItem(color.getId(), color.getName(), color.getHex()));
        }
        return groups;
    }
}
