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
 * 도매의 {@code /api/retail-gateway/**} 를 부르는
 * {@code com.ondo.retail.wholesale.listing.WholesaleListingAdapter} 가 구현한다 (MUL-88).
 *
 * <p><b>인터페이스가 여기 남아 있는 이유</b> — 이 패키지는 상대가 누구인지, HTTP 인지를
 * 몰라야 한다. 앞에 캐시를 끼우거나 도매가 부르는 방식을 바꿔도 여기는 안 고친다.
 */
public interface ListingClient {

    Page<ListingSummaryResponse> search(ListingSearchCondition condition, Pageable pageable);

    /**
     * @return 없거나 · 게시되지 않았거나 · 삭제됐으면 비어 있다
     */
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
