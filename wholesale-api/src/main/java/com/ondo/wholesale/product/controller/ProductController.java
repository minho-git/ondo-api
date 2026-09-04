package com.ondo.wholesale.product.controller;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.product.service.ProductCommandService;
import com.ondo.wholesale.product.service.ProductListQuery;
import com.ondo.wholesale.product.service.ProductQueryService;
import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.product.dto.request.ProductCreateRequest;
import com.ondo.wholesale.product.dto.response.ProductDetailResponse;
import com.ondo.wholesale.product.dto.response.ProductSummaryResponse;
import com.ondo.wholesale.product.dto.request.ProductUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 상품 API — 원본 계약: api-lite/02_상품게시. 등록(MUL-91)·목록·상세(MUL-92)는 서비스 계층이
 * 처리하고, 수정·삭제는 아직 계약 스텁(MUL-81)이다 — MUL-93·94 가 교체한다.
 */
@Tag(name = "02 상품·게시")
@RestController
@RequestMapping("/api/wholesale/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    @Operation(summary = "상품 등록 (게시글 동시 생성)", description = """
            `listing`을 함께 보내면 게시글까지 한 트랜잭션에 만든다. `listing: null` = 상품만 등록.
            `variantPrices`는 전 variant 를 빠짐없이 채운다.

            에러: 400 `CATEGORY_NOT_FOUND` · `CATEGORY_NOT_LEAF` · `COLOR_DUPLICATED` ·
            `SIZE_DUPLICATED` · `OPTION_REQUIRED` · `PRICE_REQUIRED` · `INVARIANT_VIOLATED` · `VALIDATION_FAILED`""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailResponse create(@AuthenticationPrincipal WholesalePrincipal principal,
                                        @Valid @RequestBody ProductCreateRequest request) {
        return productCommandService.create(principal.wholesalerId(), request);
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
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal WholesalePrincipal principal) {
        return productQueryService.list(principal.wholesalerId(),
                ProductListQuery.of(q, categoryId, from, to, page, size, sort));
    }

    @Operation(summary = "상품 상세 (색상·SKU·재고·게시글)", description = """
            아코디언 펼침 / 상세 패널 / 수정 화면 초기값 / 재고탭 SKU 표가 모두 이 응답을 쓴다.
            정렬은 서버 보장 — 색상은 그룹 → 색상, variant 는 사이즈, 이미지는 `sortOrder` ASC.

            에러: 404 `RESOURCE_NOT_FOUND` (없거나 타 도매처 자원)""")
    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@AuthenticationPrincipal WholesalePrincipal principal,
                                        @PathVariable Long productId) {
        return productQueryService.detail(principal.wholesalerId(), productId);
    }

    @Operation(summary = "상품 수정 (게시글 생성·수정 흡수)", description = """
            PATCH 의미론 — 생략 = 무변경, `colorOptions`·`images`·`variantPrices`는 전체 교체.
            게시글이 없으면 `listing`은 생성 분기(응답은 200). `listing.status`는 받지 않는다.

            에러: 400 `VALIDATION_FAILED` · `CATEGORY_NOT_FOUND` · `CATEGORY_NOT_LEAF` ·
            `COLOR_DUPLICATED` · `SIZE_DUPLICATED` · `OPTION_REQUIRED` · `PRICE_REQUIRED` /
            404 `RESOURCE_NOT_FOUND` / 409 `VARIANT_HAS_STOCK` · `VARIANT_ALLOCATED` ·
            `VARIANT_HAS_BACKORDER` · `VARIANT_IN_PENDING_ORDER`""")
    @PatchMapping("/{productId}")
    public ProductDetailResponse update(@PathVariable Long productId, @Valid @RequestBody ProductUpdateRequest request) {
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
