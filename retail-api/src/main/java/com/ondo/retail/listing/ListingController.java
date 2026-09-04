package com.ondo.retail.listing;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.response.ApiResponse;
import com.ondo.retail.common.response.PageResponse;
import com.ondo.retail.listing.dto.CategoryResponse;
import com.ondo.retail.listing.dto.FilterOptionsResponse;
import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 조회. 승인된 소매처만 부를 수 있다 — 승인 검사는 SecurityConfig 가 앞에서 한다.
 *
 * <p>데이터는 전부 도매 것이라 {@link ListingClient} 를 통해 가져온다.
 * 소매 DB 에는 상품이 없다 — 매 요청 도매를 부른다.
 */
@Tag(name = "상품", description = "도매 상품을 둘러본다.")
@RestController
@RequestMapping("/api/retail")
@RequiredArgsConstructor
public class ListingController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "seasonStartedAt");

    private final ListingClient listingClient;

    /**
     * 상품 목록 · 검색. 쇼핑몰 그리드와 검색 결과가 같은 엔드포인트를 쓴다.
     *
     * <p>게시 중(ON_SALE)인 상품만 나온다. 시즌 종료·삭제된 것은 목록에 없다.
     */
    @Operation(summary = "상품 목록",
               description = "검색 · 필터 · 정렬. 목록은 meta 가 붙는다.")
    @GetMapping("/listings")
    public PageResponse<ListingSummaryResponse> listings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> colorIds,
            @RequestParam(required = false) List<String> sizes,
            @RequestParam(required = false) Integer priceFrom,
            @RequestParam(required = false) Integer priceTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validate(size, priceFrom, priceTo);

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        ListingSearchCondition condition =
                new ListingSearchCondition(q, categoryId, colorIds, sizes, priceFrom, priceTo);

        return PageResponse.of(listingClient.search(condition, pageable));
    }

    /**
     * 상품 상세.
     *
     * <p>없거나 · 게시되지 않았거나 · 삭제된 상품을 구분해 알려주지 않는다.
     * 도매가 시즌을 닫은 건지 원래 없는 건지 소매가 알 이유가 없다.
     */
    @Operation(summary = "상품 상세",
               description = "색상·사이즈 조합(SKU)과 사진이 함께 온다.")
    @GetMapping("/listings/{listingId}")
    public ApiResponse<ListingDetailResponse> listing(@PathVariable Long listingId) {
        return ApiResponse.of(listingClient.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    /** 좌측 네비의 3단 트리. 고정 마스터라 프론트에서 캐시해도 된다. */
    @Operation(summary = "카테고리",
               description = "필터 화면의 카테고리 트리.")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.of(listingClient.categories());
    }

    /** 필터 사이드바를 그리는 값. 목록과 따로 부른다 — 필터를 바꿔도 선택지는 안 바뀐다. */
    @Operation(summary = "필터 항목",
               description = "색상·사이즈·가격대 등 고를 수 있는 값 전부.")
    @GetMapping("/filter-options")
    public ApiResponse<FilterOptionsResponse> filterOptions() {
        return ApiResponse.of(listingClient.filterOptions());
    }

    private static void validate(int size, Integer priceFrom, Integer priceTo) {
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (priceFrom != null && priceTo != null && priceFrom > priceTo) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
