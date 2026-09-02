package com.ondo.wholesale.product;

import com.ondo.wholesale.product.dto.ColorGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 색상 팔레트 계약 스텁 (MUL-81) — 원본: api-lite/02_상품게시/GET_colors.md. */
@Tag(name = "02 상품·게시")
@RestController
public class ColorController {

    @Operation(summary = "색상 팔레트", description = """
            그룹 → 색상 2단 구조, 고정 시드라 프론트 캐시 가능. 페이징 없음(`meta` 없이 `data`만).
            상품에 넣을 수 있는 색은 이 목록이 전부다.""")
    @GetMapping("/api/wholesale/colors")
    public List<ColorGroupResponse> colors() {
        return ProductStubExamples.colorPalette();
    }
}
