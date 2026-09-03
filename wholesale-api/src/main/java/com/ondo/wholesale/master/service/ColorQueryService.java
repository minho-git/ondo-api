package com.ondo.wholesale.master.service;

import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.master.dto.ColorGroupResponse;
import com.ondo.wholesale.master.repository.ColorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** 색상 팔레트 조회 (MUL-90). 그룹 → 그룹 내 순서는 쿼리가 보장하고 여기선 그룹핑만 한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ColorQueryService {

    private final ColorRepository colorRepository;

    public List<ColorGroupResponse> palette() {
        // 같은 영속성 컨텍스트라 같은 id 의 ColorGroup 은 동일 인스턴스 — 엔티티 키 그룹핑이 안전하다
        return colorRepository.findAllWithGroupOrdered().stream()
                .collect(Collectors.groupingBy(Color::getGroup, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new ColorGroupResponse(e.getKey().getId(), e.getKey().getName(),
                        e.getValue().stream()
                                .map(c -> new ColorGroupResponse.ColorItem(c.getId(), c.getName(), c.getHex()))
                                .toList()))
                .toList();
    }
}
