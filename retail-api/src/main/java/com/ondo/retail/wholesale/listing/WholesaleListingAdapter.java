package com.ondo.retail.wholesale.listing;

import com.ondo.retail.listing.ListingClient;
import com.ondo.retail.listing.dto.CategoryResponse;
import com.ondo.retail.listing.dto.FilterOptionsResponse;
import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
import com.ondo.retail.listing.dto.VariantInfo;
import com.ondo.retail.wholesale.WholesaleApiException;
import com.ondo.retail.wholesale.listing.dto.WholesaleCategory;
import com.ondo.retail.wholesale.listing.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.listing.dto.WholesaleFilterOptions;
import com.ondo.retail.wholesale.listing.dto.WholesaleListingDetail;
import com.ondo.retail.wholesale.listing.dto.WholesaleListingSummary;
import com.ondo.retail.wholesale.listing.dto.WholesaleVariantInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * 도매 상품 API 를 소매 말로 옮긴다 (MUL-88).
 *
 * <p>{@code MockListingClient} 를 대신한다. 소매 컨트롤러와 DTO 는 안 바뀌었다 —
 * {@link ListingClient} 라는 문 뒤에서 목이 진짜로 갈렸을 뿐이다.
 *
 * <p><b>여기가 충격 흡수 지점이다.</b> 도매 응답({@code Wholesale…})과 소매 응답은
 * 지금 모양이 같지만 타입이 다르다. 채빈이 도매에서 필드를 바꾸면 이 클래스가
 * 컴파일 에러로 먼저 막는다 — 그게 프론트까지 조용히 흘러가면 창은이 화면이
 * 이유 없이 깨지고, 채빈은 자기가 뭘 깼는지 모른다.
 */
@Component
@RequiredArgsConstructor
public class WholesaleListingAdapter implements ListingClient {

    /** 도매가 한 번에 받는 최대 개수. 도매 쪽 상한과 같은 값이어야 한다. */
    private static final int MAX_VARIANT_IDS_PER_CALL = 200;

    private final WholesaleListingApi api;

    @Override
    public Page<ListingSummaryResponse> search(ListingSearchCondition condition, Pageable pageable) {
        WholesaleEnvelope<List<WholesaleListingSummary>> response = call("상품 목록", () ->
                api.listings(
                        condition.q(),
                        condition.categoryId(),
                        condition.colorIds(),
                        condition.sizes(),
                        condition.priceFrom(),
                        condition.priceTo(),
                        pageable.getPageNumber(),
                        pageable.getPageSize()));

        List<ListingSummaryResponse> content = response.data().stream().map(WholesaleListingAdapter::toSummary).toList();

        // 전체 개수는 도매만 안다. meta 가 없으면 이 장이 전부인 것으로 본다
        long total = response.meta() != null ? response.meta().totalElements() : content.size();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 상세.
     *
     * <p>도매의 404 를 빈 값으로 바꾼다. 도매는 없는 것 · 시즌이 끝난 것 · 지워진 것을
     * 구분하지 않고 전부 404 로 주고, 소매도 구분해서 알려주지 않는다.
     */
    @Override
    public Optional<ListingDetailResponse> findById(Long listingId) {
        try {
            return Optional.of(toDetail(api.listing(listingId).data()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new WholesaleApiException("도매를 부르지 못했습니다. 요청=상품 상세 listingId=" + listingId, e);
        }
    }

    @Override
    public List<CategoryResponse> categories() {
        return call("카테고리", api::categories).data().stream()
                .map(WholesaleListingAdapter::toCategory)
                .toList();
    }

    @Override
    public FilterOptionsResponse filterOptions() {
        WholesaleFilterOptions source = call("필터 항목", api::filterOptions).data();

        List<FilterOptionsResponse.ColorGroup> groups = source.colorGroups().stream()
                .map(g -> new FilterOptionsResponse.ColorGroup(g.id(), g.name(),
                        g.colors().stream()
                                .map(c -> new FilterOptionsResponse.Color(c.id(), c.name(), c.hex()))
                                .toList()))
                .toList();

        return new FilterOptionsResponse(groups, source.sizes(),
                new FilterOptionsResponse.PriceRange(source.priceRange().min(), source.priceRange().max()));
    }

    /**
     * 옵션 배치 조회.
     *
     * <p>빈 목록이면 도매를 안 부른다 — 장바구니가 비었을 때까지 왕복할 이유가 없고,
     * 도매는 빈 {@code ids} 를 400 으로 막는다.
     */
    @Override
    public Map<Long, VariantInfo> findVariants(List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }

        // 도매가 한 번에 200개까지만 받는다. 장바구니 줄 수에는 상한이 없어서,
        // 그대로 보내면 줄이 많은 소매처는 장바구니 화면 전체가 안 열린다
        List<WholesaleVariantInfo> found = new ArrayList<>();
        for (int from = 0; from < variantIds.size(); from += MAX_VARIANT_IDS_PER_CALL) {
            List<Long> chunk = variantIds.subList(from,
                    Math.min(from + MAX_VARIANT_IDS_PER_CALL, variantIds.size()));
            found.addAll(call("옵션 조회", () -> api.variants(chunk)).data());
        }

        // 부른 순서를 지킨다. 장바구니가 담긴 순서대로 그리기 때문이다
        Map<Long, WholesaleVariantInfo> source = new LinkedHashMap<>();
        for (WholesaleVariantInfo info : found) {
            source.put(info.variantId(), info);
        }

        Map<Long, VariantInfo> byId = new LinkedHashMap<>();
        for (Long id : variantIds) {
            WholesaleVariantInfo info = source.get(id);
            if (info != null) {
                byId.put(id, toVariantInfo(info));
            }
        }
        return byId;
    }

    /**
     * 도매 호출을 감싸 실패를 하나로 모은다.
     *
     * <p>{@link RestClientException} 하나로 잡는 이유 — 도매가 안 떠 있는 것도(연결 거부),
     * 제때 안 주는 것도(타임아웃), 5xx 도 소매 입장에선 같은 일이다. 소매가 할 수 있는 게
     * 없고 사용자에게 할 말도 같다.
     */
    private static <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new WholesaleApiException("도매를 부르지 못했습니다. 요청=" + what, e);
        }
    }

    // ── 도매 말 → 소매 말 ───────────────────────────────────────

    private static ListingSummaryResponse toSummary(WholesaleListingSummary source) {
        return new ListingSummaryResponse(
                source.listingId(),
                source.title(),
                new ListingSummaryResponse.WholesalerBrief(source.wholesaler().id(), source.wholesaler().name()),
                source.thumbnailUrl(),
                source.minSalePrice(),
                source.colorCount(),
                source.sizeCount(),
                source.isSinglePieceAllowed());
    }

    private static ListingDetailResponse toDetail(WholesaleListingDetail source) {
        return new ListingDetailResponse(
                source.listingId(),
                source.title(),
                source.description(),
                source.productNumber(),
                source.isSinglePieceAllowed(),
                source.minSalePrice(),
                source.maxSalePrice(),
                source.listedVariantCount(),
                source.totalVariantCount(),
                source.categoryPath().stream()
                        .map(c -> new ListingDetailResponse.CategoryNode(c.id(), c.name()))
                        .toList(),
                new ListingDetailResponse.Wholesaler(
                        source.wholesaler().id(),
                        source.wholesaler().name(),
                        source.wholesaler().storeBuilding(),
                        source.wholesaler().storeUnit()),
                source.colorOptions().stream()
                        .map(WholesaleListingAdapter::toColorOption)
                        .toList(),
                source.images().stream()
                        .map(i -> new ListingDetailResponse.Image(i.id(), i.url(), i.sortOrder()))
                        .toList());
    }

    private static ListingDetailResponse.ColorOption toColorOption(WholesaleListingDetail.ColorOption source) {
        return new ListingDetailResponse.ColorOption(
                new ListingDetailResponse.Color(
                        source.color().id(),
                        source.color().name(),
                        source.color().hex(),
                        source.color().groupName()),
                source.variants().stream()
                        .map(v -> new ListingDetailResponse.Variant(v.id(), v.size(), v.salePrice(), v.orderLimit()))
                        .toList());
    }

    private static CategoryResponse toCategory(WholesaleCategory source) {
        return new CategoryResponse(source.id(), source.name(),
                source.children().stream().map(WholesaleListingAdapter::toCategory).toList());
    }

    private static VariantInfo toVariantInfo(WholesaleVariantInfo source) {
        return new VariantInfo(
                source.variantId(),
                source.listingId(),
                source.title(),
                source.thumbnailUrl(),
                source.colorName(),
                source.size(),
                source.salePrice(),
                source.orderLimit(),
                source.wholesalerId(),
                source.wholesalerName(),
                source.orderable());
    }
}
