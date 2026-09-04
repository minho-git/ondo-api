package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.ListingVariant;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.dto.request.ListingUpsertRequest;
import com.ondo.wholesale.product.dto.request.VariantPriceRequest;
import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ListingVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시글과 판매가에 관한 모든 쓰기 규칙 (MUL-91·93).
 *
 * <p>등록의 생성, 수정의 upsert(생성 분기 포함), 판매가 전체 교체, 그리고
 * "게시 상품의 살아있는 전 variant 는 판매가가 있어야 한다"는 불변식 검증까지 —
 * 게시·가격이 바뀌는 길은 전부 이 클래스를 지난다.
 */
@Component
@RequiredArgsConstructor
public class ListingWriter {

    private final ListingRepository listingRepository;
    private final ListingVariantRepository listingVariantRepository;

    /** 등록·수정 공용 생성 분기. title 필수, images 생략은 "없음"으로 본다. */
    public void create(Product product, ListingUpsertRequest request, boolean creating) {
        if (request.title() == null || request.title().isBlank()) {
            throw blankTitle();
        }
        Listing listing = Listing.builder()
                .product(product)
                .title(request.title())
                .description(emptyToNull(request.description()))
                .singlePieceAllowed(Boolean.TRUE.equals(request.isSinglePieceAllowed()))
                .build();
        listing.replaceImages(request.images() == null ? List.of() : request.images());
        listingRepository.save(listing);

        replacePrices(product, listing, request.variantPrices() == null ? List.of() : request.variantPrices(), creating);
    }

    /** 게시글 upsert — 없으면 생성 분기, 있으면 내부 필드별 "null = 무변경" 갱신. */
    public void upsert(Product product, ListingUpsertRequest request) {
        Listing listing = listingRepository.findByProductIdAndDeletedAtIsNull(product.getId()).orElse(null);
        if (listing == null) {
            create(product, request, false);
            return;
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw blankTitle();
            }
            listing.updateTitle(request.title());
        }
        if (request.description() != null) {
            listing.updateDescription(emptyToNull(request.description()));
        }
        if (request.isSinglePieceAllowed() != null) {
            listing.updateSinglePieceAllowed(request.isSinglePieceAllowed());
        }
        if (request.images() != null) {
            listing.replaceImages(request.images());
        }
        if (request.variantPrices() != null) {
            replacePrices(product, listing, request.variantPrices(), false);
        }
    }

    /**
     * 게시 상품의 불변식 — 살아있는 전 variant 는 판매가가 있어야 한다.
     * variantPrices 없이 colorOptions 로 variant 만 추가하는 경우를 여기서 잡는다.
     */
    public void verifyPriceCoverage(Product product) {
        Listing listing = listingRepository.findByProductIdAndDeletedAtIsNull(product.getId()).orElse(null);
        if (listing == null) {
            return;
        }
        Set<Long> priced = listingVariantRepository.findByIdListingId(listing.getId()).stream()
                .map(lv -> lv.getId().getVariantId())
                .collect(Collectors.toSet());
        boolean uncovered = product.getColorOptions().stream()
                .flatMap(option -> option.getVariants().stream())
                .filter(Variant::isAlive)
                .anyMatch(v -> v.getId() == null || !priced.contains(v.getId()));
        if (uncovered) {
            throw new ApiException(ErrorCode.PRICE_REQUIRED);
        }
    }

    /**
     * 판매가 전체 교체 — (요청 반영 후) 살아있는 전 variant 를 정확히 1건씩 덮어야 한다.
     * 누락 = PRICE_REQUIRED, 이 상품의 살아있는 variant 로 해석 안 되는 지시(남의 variantId 포함)
     * = INVARIANT_VIOLATED, 같은 variant 중복 = VALIDATION_FAILED.
     * 지정 방식은 variantId 든 (colorId, size) 든 해석되면 허용한다(팀 결정 2026-09-04).
     * 단 등록(creating)은 전부 신규라 variantId 지정 자체가 400 이다(계약).
     */
    private void replacePrices(Product product, Listing listing, List<VariantPriceRequest> prices, boolean creating) {
        Map<String, Variant> aliveByColorAndSize = new HashMap<>();
        Map<Long, Variant> aliveById = new HashMap<>();
        product.getColorOptions().forEach(option -> option.getVariants().stream()
                .filter(Variant::isAlive)
                .forEach(v -> {
                    aliveByColorAndSize.put(key(option.getColor().getId(), v.getSize()), v);
                    aliveById.put(v.getId(), v);
                }));

        Map<Long, VariantPriceRequest> covered = new HashMap<>();
        for (VariantPriceRequest price : prices) {
            if (creating && price.variantId() != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        List.of(new ErrorResponse.FieldError("listing.variantPrices.variantId",
                                "등록에서는 variantId 를 지정할 수 없습니다.")));
            }
            Variant variant = (price.variantId() != null)
                    ? aliveById.get(price.variantId())
                    : aliveByColorAndSize.get(key(price.colorId(), price.size()));
            if (variant == null) {
                throw new ApiException(ErrorCode.INVARIANT_VIOLATED);
            }
            if (covered.put(variant.getId(), price) != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        List.of(new ErrorResponse.FieldError("listing.variantPrices", "같은 variant 를 두 번 덮었습니다.")));
            }
        }
        if (covered.size() < aliveByColorAndSize.size()) {
            throw new ApiException(ErrorCode.PRICE_REQUIRED);
        }
        Map<Long, ListingVariant> existing = listingVariantRepository.findByIdListingId(listing.getId()).stream()
                .collect(Collectors.toMap(lv -> lv.getId().getVariantId(), lv -> lv));
        covered.forEach((variantId, price) -> {
            ListingVariant row = existing.get(variantId);
            if (row != null) {
                row.reprice(price.salePrice(), price.orderLimit());
            } else {
                listingVariantRepository.save(ListingVariant.builder()
                        .listingId(listing.getId()).variantId(variantId)
                        .salePrice(price.salePrice()).orderLimit(price.orderLimit())
                        .build());
            }
        });
    }

    private ApiException blankTitle() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new ErrorResponse.FieldError("listing.title", "게시글 제목은 비울 수 없습니다.")));
    }

    /** description 은 빈 문자열이 "지움"이다 — 저장은 null 로 한다. */
    private String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private String key(Long colorId, Size size) {
        return colorId + "/" + size.name();
    }
}
