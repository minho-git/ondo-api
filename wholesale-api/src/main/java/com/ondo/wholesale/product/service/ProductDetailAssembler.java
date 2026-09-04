package com.ondo.wholesale.product.service;

import com.ondo.wholesale.master.service.CategoryTree;

import com.ondo.wholesale.product.domain.ColorOption;
import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.ListingVariant;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.dto.response.ColorOptionResponse;
import com.ondo.wholesale.product.dto.response.ColorResponse;
import com.ondo.wholesale.product.dto.response.ListingImageResponse;
import com.ondo.wholesale.product.dto.response.ListingResponse;
import com.ondo.wholesale.product.dto.response.ProductDetailResponse;
import com.ondo.wholesale.product.dto.response.VariantResponse;
import com.ondo.wholesale.product.repository.ColorOptionRepository;
import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ListingVariantRepository;
import com.ondo.wholesale.product.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 상세 응답 조립 — 등록·수정·단건 조회가 같은 스키마를 쓰므로 한 곳에 모은다.
 *
 * <p>서버 보장 정렬이 전부 여기 있다: 색상 = 그룹 sortOrder → 색 sortOrder,
 * variant = {@link com.ondo.wholesale.product.domain.Size} 선언 순, 이미지 = sortOrder ASC.
 * 살아있는 variant 가 없는 색상 옵션은 숨긴다 (색 제거의 관측 결과).
 */
@Component
@RequiredArgsConstructor
public class ProductDetailAssembler {

    private final ColorOptionRepository colorOptionRepository;
    private final VariantRepository variantRepository;
    private final ListingRepository listingRepository;
    private final ListingVariantRepository listingVariantRepository;
    private final VariantUsageChecker variantUsageChecker;
    private final CategoryTree categoryTree;

    private static final Comparator<ColorOption> COLOR_ORDER = Comparator
            .comparingInt((ColorOption co) -> co.getColor().getGroup().getSortOrder())
            .thenComparing(co -> co.getColor().getGroup().getId())
            .thenComparingInt(co -> co.getColor().getSortOrder())
            .thenComparing(co -> co.getColor().getId());

    public ProductDetailResponse assemble(Product product) {
        List<ColorOption> options = colorOptionRepository.findByProductIdWithColor(product.getId());
        Map<Long, List<Variant>> variantsByOption = variantRepository
                .findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .collect(Collectors.groupingBy(v -> v.getColorOption().getId()));

        Listing listing = listingRepository.findByProductIdAndDeletedAtIsNull(product.getId()).orElse(null);
        Map<Long, ListingVariant> prices = (listing == null) ? Map.of()
                : listingVariantRepository.findByIdListingId(listing.getId()).stream()
                        .collect(Collectors.toMap(lv -> lv.getId().getVariantId(), Function.identity()));

        List<Long> variantIds = variantsByOption.values().stream()
                .flatMap(List::stream).map(Variant::getId).toList();
        Map<Long, Integer> backorders = variantUsageChecker.backorderQtyByVariant(variantIds);

        List<ColorOptionResponse> colorOptions = options.stream()
                .filter(co -> !variantsByOption.getOrDefault(co.getId(), List.of()).isEmpty())
                .sorted(COLOR_ORDER)
                .map(co -> new ColorOptionResponse(
                        new ColorResponse(co.getColor().getId(), co.getColor().getName(),
                                co.getColor().getHex(), co.getColor().getGroup().getName()),
                        variantsByOption.get(co.getId()).stream()
                                .sorted(Comparator.comparing(Variant::getSize))
                                .map(v -> toVariantResponse(v, prices.get(v.getId()), backorders))
                                .toList()))
                .toList();

        return new ProductDetailResponse(
                product.getId(), product.getProductNumber(), product.getName(),
                categoryTree.pathOf(product.getCategoryId()),
                colorOptions,
                toListingResponse(listing));
    }

    private VariantResponse toVariantResponse(Variant variant, ListingVariant price,
                                              Map<Long, Integer> backorders) {
        int allocated = variant.getReservedQty();
        return new VariantResponse(
                variant.getId(), variant.getVariantSeq(), variant.getSize(),
                variant.getStockQty(), allocated,
                backorders.getOrDefault(variant.getId(), 0),
                variant.getStockQty() - allocated, // 미송은 빼지 않는다 (계약)
                variant.getAvgCost(),
                price == null ? null : price.getSalePrice(),
                price == null ? null : price.getOrderLimit());
    }

    /** 시즌 전이 응답도 같은 스키마다 — 게시글 → ListingResponse 변환의 유일한 자리. */
    public ListingResponse toListingResponse(Listing listing) {
        if (listing == null) {
            return null;
        }
        List<ListingImageResponse> images = listing.getImages().stream()
                .sorted(Comparator.comparingInt(com.ondo.wholesale.product.domain.ListingImage::getSortOrder))
                .map(i -> new ListingImageResponse(i.getId(), i.getUrl(), i.getSortOrder()))
                .toList();
        return new ListingResponse(listing.getId(), listing.getStatus(), listing.getTitle(),
                listing.getDescription(), listing.isSinglePieceAllowed(),
                listing.getSeasonStartedAt(), listing.getSeasonEndedAt(), images);
    }
}
