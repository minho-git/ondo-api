package com.ondo.retail.wholesale.listing;

import com.ondo.retail.wholesale.listing.dto.WholesaleCategory;
import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.listing.dto.WholesaleFilterOptions;
import com.ondo.retail.wholesale.listing.dto.WholesaleListingDetail;
import com.ondo.retail.wholesale.listing.dto.WholesaleListingSummary;
import com.ondo.retail.wholesale.listing.dto.WholesaleVariantInfo;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 도매 소매접점 상품 API 의 계약 (MUL-88).
 *
 * <p><b>구현체가 없다.</b> 앱이 뜰 때 스프링이 이 인터페이스를 보고 만들어 끼운다
 * ({@code WholesaleHttpConfig} 의 {@code @ImportHttpServices}).
 *
 * <p>주소를 문자열로 붙이지 않는 이유가 여기 있다. {@code @RequestParam} 으로 넘기면
 * 인코딩을 스프링이 한다 — 검색어에 {@code %} 나 {@code |} 가 섞여도 우리가 깨뜨릴
 * 자리가 없다. 손으로 이어 붙이면 브라우저 같은 안전장치가 없어서 그대로 터진다
 * (숙제 1-2절).
 *
 * <p>null 인 파라미터는 스프링이 아예 안 붙인다. 그래서 필터를 안 건 경우
 * {@code ?q=null} 같은 게 나가지 않는다.
 */
@HttpExchange
public interface WholesaleListingApi {

    /** 목록 · 검색. 정렬은 도매가 최신순으로 고정한다 — 축을 고를 수 없다. */
    @GetExchange("/api/retail-gateway/listings")
    WholesaleEnvelope<List<WholesaleListingSummary>> listings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> colorIds,
            @RequestParam(required = false) List<String> sizes,
            @RequestParam(required = false) Integer priceFrom,
            @RequestParam(required = false) Integer priceTo,
            @RequestParam int page,
            @RequestParam int size);

    /** 상세. 게시 중이 아니면 도매가 404 를 준다. */
    @GetExchange("/api/retail-gateway/listings/{listingId}")
    WholesaleEnvelope<WholesaleListingDetail> listing(@PathVariable Long listingId);

    @GetExchange("/api/retail-gateway/categories")
    WholesaleEnvelope<List<WholesaleCategory>> categories();

    @GetExchange("/api/retail-gateway/filter-options")
    WholesaleEnvelope<WholesaleFilterOptions> filterOptions();

    /** 옵션 배치. 담긴 개수만큼 한 건씩 부르지 않으려고 묶어서 받는다. */
    @GetExchange("/api/retail-gateway/variants")
    WholesaleEnvelope<List<WholesaleVariantInfo>> variants(@RequestParam List<Long> ids);
}
