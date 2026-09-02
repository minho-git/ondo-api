package com.ondo.retail.listing;

import com.ondo.retail.listing.dto.CategoryResponse;
import com.ondo.retail.listing.dto.FilterOptionsResponse;
import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
import com.ondo.retail.listing.dto.VariantInfo;
import java.util.Map;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 도매에서 상품 정보를 가져오는 통로.
 *
 * <p>상품은 전부 도매 것이고 DB 가 갈라져 있어서 소매가 직접 못 읽는다.
 * 지금은 {@link MockListingClient} 가 가짜 값을 돌려주고,
 * 도매 시드와 내부 API 가 준비되면 {@code /api/internal/**} 을 부르는 구현체로 갈아끼운다.
 *
 * <p>응답 모양이 명세 그대로라 갈아끼울 때 컨트롤러와 DTO 는 안 고친다.
 */
public interface ListingClient {

    Page<ListingSummaryResponse> search(ListingSearchCondition condition, Pageable pageable);

    /** @return 없거나 · 게시되지 않았거나 · 삭제됐으면 비어 있다 */
    java.util.Optional<ListingDetailResponse> findById(Long listingId);

    List<CategoryResponse> categories();

    FilterOptionsResponse filterOptions();

    /**
     * 옵션 여러 개를 한 번에 가져온다. 장바구니가 쓴다.
     *
     * <p>한 건씩 부르면 장바구니에 담긴 개수만큼 호출이 늘어난다. 묶어서 한 번에 받는다.
     *
     * @return 찾은 것만 담긴다. 지워진 옵션은 키가 없다
     */
    Map<Long, VariantInfo> findVariants(List<Long> variantIds);
}
