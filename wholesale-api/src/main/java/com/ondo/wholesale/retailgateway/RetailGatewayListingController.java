package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.retailgateway.dto.RetailCategoryResponse;
import com.ondo.wholesale.retailgateway.dto.RetailFilterOptionsResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingDetailResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingSearchCondition;
import com.ondo.wholesale.retailgateway.dto.RetailListingSummaryResponse;
import com.ondo.wholesale.retailgateway.dto.RetailVariantInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 소매 상품 조회 (MUL-88) — 소매 백엔드가 부른다.
 *
 * <p>인증 주체가 도매처 본인이 아니라 소매 백엔드라 경로가 {@code /api/wholesale} 밖이다.
 * 인증 축은 미확정(U-14-B2)이라 {@link com.ondo.wholesale.config.SecurityConfig} 가
 * {@code /api/retail-gateway/**} 를 잠정 permitAll 로 열어 뒀다. MUL-87 에서 닫는다.
 *
 * <p>채빈의 {@code ProductController} 와 겹치지 않는다. 그쪽은 도매 POS 화면용이라
 * 도매처 본인 상품만 보여주고 재고·원가가 실린다. 여기는 소매용이라 게시 중인 것만,
 * 도매처를 안 가리고, 재고를 안 내린다.
 *
 * <p><b>정렬은 최신순 고정이다.</b> 소매 화면에 정렬 선택이 없어서 축을 열지 않았다.
 * 필요해지면 {@code sort} 파라미터를 그때 연다.
 */
@Tag(name = "08 소매접점")
@RestController
@RequestMapping("/api/retail-gateway")
@RequiredArgsConstructor
public class RetailGatewayListingController {

    /** 한 번에 가져갈 수 있는 최대. 소매도 같은 값으로 막지만 여기가 최종 방어다. */
    private static final int MAX_PAGE_SIZE = 100;

    /** 장바구니 한 개가 이보다 많을 수 없다. IN 절이 무한정 길어지는 걸 막는다. */
    private static final int MAX_VARIANT_IDS = 200;

    private final RetailGatewayListingService service;

    @Operation(summary = "상품 목록 (소매 백엔드 → 도매)", description = """
            게시 중(`ON_SALE`)인 상품만. 도매처를 가리지 않는다 — 소매 쇼핑몰은 여러 도매처를
            한 화면에 섞어 보여준다. 정렬은 시즌 시작 최신순 고정.

            `colorIds`·`sizes`·`priceFrom`·`priceTo` 는 옵션에 걸리는 조건이다. 하나라도 맞으면
            그 상품이 나온다. 카드의 `minSalePrice`·`colorCount`·`sizeCount` 는 필터와 무관하게
            그 상품의 전체 옵션 기준이다.

            에러: 400 `VALIDATION_FAILED` (`size > 100` / `priceFrom > priceTo`)""")
    @GetMapping("/listings")
    public ApiResponse<List<RetailListingSummaryResponse>> listings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> colorIds,
            @RequestParam(required = false) List<String> sizes,
            @RequestParam(required = false) Integer priceFrom,
            @RequestParam(required = false) Integer priceTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePaging(page, size);
        validatePriceRange(priceFrom, priceTo);

        RetailListingSearchCondition condition =
                new RetailListingSearchCondition(q, categoryId, colorIds, sizes, priceFrom, priceTo);
        Paged<RetailListingSummaryResponse> result =
                service.search(condition, page, size);

        int totalPages = (int) Math.ceil((double) result.totalElements() / size);
        return ApiResponse.paged(result.content(),
                new ApiResponse.PageMeta(page, size, result.totalElements(), totalPages));
    }

    @Operation(summary = "상품 상세 (소매 백엔드 → 도매)", description = """
            색상 × 사이즈 조합과 사진이 함께 온다. 정렬은 서버 보장 — 색상은 그룹 → 색상,
            사이즈는 XS~FREE, 이미지는 `sortOrder` ASC.

            게시 중이 아니면 404 다. 없는 것 · 시즌이 끝난 것 · 지워진 것을 구분하지 않는다.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/listings/{listingId}")
    public RetailListingDetailResponse listing(@PathVariable Long listingId) {
        return service.detail(listingId);
    }

    @Operation(summary = "카테고리 트리 (소매 백엔드 → 도매)", description = """
            소매 좌측 네비의 3단 트리. 고정 마스터라 소매가 캐시해도 된다.
            도매 화면용 `GET /api/wholesale/categories` 와 달리 `depth` 를 안 싣는다.""")
    @GetMapping("/categories")
    public List<RetailCategoryResponse> categories() {
        return service.categories();
    }

    @Operation(summary = "필터 항목 (소매 백엔드 → 도매)", description = """
            색상·사이즈는 마스터 전체다 — 지금 파는 상품에 없는 색도 들어간다. 선택지가
            필터 결과에 따라 흔들리면 화면이 튄다. 가격만 게시 중인 상품의 실제 최저·최고가다.""")
    @GetMapping("/filter-options")
    public RetailFilterOptionsResponse filterOptions() {
        return service.filterOptions();
    }

    @Operation(summary = "옵션 배치 조회 (소매 백엔드 → 도매)", description = """
            소매 장바구니가 쓴다. 담긴 개수만큼 한 건씩 부르지 않게 묶어서 받는다.

            게시가 내려간 옵션도 돌려주되 `orderable: false` 로 온다 — 담아둔 사이 도매가
            시즌을 닫아도 소매 장바구니 행은 남아 있어서, 안 주면 소매가 그 줄을 못 그린다.
            게시 자체가 없는 옵션은 응답에 안 들어간다.

            에러: 400 `VALIDATION_FAILED` (`ids` 가 비었거나 200개 초과)""")
    @GetMapping("/variants")
    public List<RetailVariantInfoResponse> variants(@RequestParam List<Long> ids) {
        if (ids.isEmpty() || ids.size() > MAX_VARIANT_IDS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "ids 는 1개 이상 %d개 이하여야 합니다.".formatted(MAX_VARIANT_IDS));
        }
        return service.variants(ids);
    }

    private static void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "page 는 0 이상, size 는 1 이상 %d 이하여야 합니다.".formatted(MAX_PAGE_SIZE));
        }
    }

    private static void validatePriceRange(Integer priceFrom, Integer priceTo) {
        if (priceFrom != null && priceTo != null && priceFrom > priceTo) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "priceFrom 이 priceTo 보다 큽니다.");
        }
    }
}
