package com.ondo.wholesale.product.controller;

import com.ondo.wholesale.product.domain.ListingStatus;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.dto.CategoryPathItem;
import com.ondo.wholesale.product.dto.ColorOptionResponse;
import com.ondo.wholesale.product.dto.ColorResponse;
import com.ondo.wholesale.product.dto.ListingImageResponse;
import com.ondo.wholesale.product.dto.ListingResponse;
import com.ondo.wholesale.product.dto.ProductDetailResponse;
import com.ondo.wholesale.product.dto.ProductSummaryResponse;
import com.ondo.wholesale.product.dto.VariantResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 계약 스텁이 반환하는 example 값 — api-lite/02_상품게시 문서의 Response 예시 그대로.
 * 실구현이 들어오면 이 클래스만 서비스 호출로 교체된다.
 */
final class ProductStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private ProductStubExamples() {
    }

    static ProductDetailResponse productDetail() {
        VariantResponse variant = new VariantResponse(
                90231L, 1, Size.XS, 33, 0, 2, 33,
                new BigDecimal("15200.00"), 29000, 100);
        ColorOptionResponse black = new ColorOptionResponse(
                new ColorResponse(1L, "블랙", "#191F28", "무채색"), List.of(variant));
        return new ProductDetailResponse(
                5012L, 18, "오버핏 코튼 티셔츠", categoryPath(), List.of(black), listing(ListingStatus.ON_SALE));
    }

    static List<ProductSummaryResponse> productSummaries() {
        return List.of(new ProductSummaryResponse(
                5012L, 18, "오버핏 코튼 티셔츠", categoryPath(), ListingStatus.ON_SALE, 3, 8));
    }

    static ListingResponse listing(ListingStatus status) {
        boolean ended = status == ListingStatus.SEASON_ENDED;
        return new ListingResponse(
                4410L, status,
                "[신상] 오버핏 코튼 티셔츠 데일리 남방",
                "넉넉한 오버핏 실루엣의 데일리 셔츠예요.",
                true,
                OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, KST),
                ended ? OffsetDateTime.of(2026, 8, 24, 15, 20, 0, 0, KST) : null,
                List.of(new ListingImageResponse(8801L, "https://cdn.ondo.example/listings/4410/1.jpg", 0)));
    }

    private static List<CategoryPathItem> categoryPath() {
        return List.of(
                new CategoryPathItem(1L, "여성"),
                new CategoryPathItem(12L, "상의"),
                new CategoryPathItem(121L, "티셔츠"));
    }
}
