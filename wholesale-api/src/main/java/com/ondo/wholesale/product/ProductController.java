package com.ondo.wholesale.product;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.product.dto.ProductCreateRequest;
import com.ondo.wholesale.product.dto.ProductDetailResponse;
import com.ondo.wholesale.product.dto.ProductSummaryResponse;
import com.ondo.wholesale.product.dto.ProductUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 상품 계약 스텁 (MUL-81) — 원본: api-lite/02_상품게시. example 응답만 반환하며
 * 실구현(MUL-46)이 서비스 계층으로 교체한다. 인증·봉투는 실서버와 동일하게 동작한다.
 */
@Tag(name = "02 상품·게시")
@RestController
@RequestMapping("/api/wholesale/products")
public class ProductController {

    @Operation(summary = "상품 등록 (게시글 동시 생성)", description = """
            `listing`을 함께 보내면 게시글까지 한 트랜잭션에 만든다. `listing: null` = 상품만 등록.
            `variantPrices`는 전 variant 를 빠짐없이 채운다.

            에러: 400 `CATEGORY_NOT_FOUND` · `CATEGORY_NOT_LEAF` · `COLOR_DUPLICATED` ·
            `SIZE_DUPLICATED` · `OPTION_REQUIRED` · `PRICE_REQUIRED` · `INVARIANT_VIOLATED` · `VALIDATION_FAILED`""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailResponse create(@RequestBody ProductCreateRequest request) {
        return ProductStubExamples.productDetail();
    }

    @Operation(summary = "상품 목록 (아코디언 헤더)", description = """
            상품탭·재고탭 공용. 기간 필터 축은 상품 등록일(`createdAt`) 하나다.
            `categoryId`는 하위 카테고리 포함. 기본 정렬 `createdAt,desc`.

            에러: 400 `VALIDATION_FAILED` (`from > to` / `size > 100`)""")
    @GetMapping
    public ApiResponse<List<ProductSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.paged(
                ProductStubExamples.productSummaries(),
                new ApiResponse.PageMeta(0, 20, 137, 7));
    }

    @Operation(summary = "상품 상세 (색상·SKU·재고·게시글)", description = """
            아코디언 펼침 / 상세 패널 / 수정 화면 초기값 / 재고탭 SKU 표가 모두 이 응답을 쓴다.
            정렬은 서버 보장 — 색상은 그룹 → 색상, variant 는 사이즈, 이미지는 `sortOrder` ASC.

            에러: 404 `RESOURCE_NOT_FOUND` (없거나 타 도매처 자원)""")
    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable Long productId) {
        return ProductStubExamples.productDetail();
    }

    @Operation(summary = "상품 수정 (게시글 생성·수정 흡수)", description = """
            PATCH 의미론 — 생략 = 무변경, `colorOptions`·`images`·`variantPrices`는 전체 교체.
            게시글이 없으면 `listing`은 생성 분기(응답은 200). `listing.status`는 받지 않는다.

            에러: 400 `VALIDATION_FAILED` · `CATEGORY_NOT_FOUND` · `CATEGORY_NOT_LEAF` ·
            `COLOR_DUPLICATED` · `SIZE_DUPLICATED` · `OPTION_REQUIRED` · `PRICE_REQUIRED` /
            404 `RESOURCE_NOT_FOUND` / 409 `VARIANT_HAS_STOCK` · `VARIANT_ALLOCATED` ·
            `VARIANT_HAS_BACKORDER` · `VARIANT_IN_PENDING_ORDER`""")
    @PatchMapping("/{productId}")
    public ProductDetailResponse update(@PathVariable Long productId, @RequestBody ProductUpdateRequest request) {
        return ProductStubExamples.productDetail();
    }

    @Operation(summary = "상품 삭제 (게시글 동반)", description = """
            게시글을 먼저 지우고 상품을 지운다. 되살리는 경로는 없고 품번은 영구 결번.
            화면의 "게시글 삭제" 버튼도 이 호출이다 — 상품까지 지워진다.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `VARIANT_HAS_STOCK` · `VARIANT_ALLOCATED` ·
            `VARIANT_HAS_BACKORDER` · `VARIANT_IN_PENDING_ORDER`""")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long productId) {
    }
}
