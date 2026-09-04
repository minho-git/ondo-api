package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.master.service.CategoryTree;

import com.ondo.wholesale.product.domain.ColorOption;
import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.ListingVariant;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.domain.Variant;
import com.ondo.wholesale.product.dto.request.ColorOptionRequest;
import com.ondo.wholesale.product.dto.request.ListingUpsertRequest;
import com.ondo.wholesale.product.dto.request.ProductCreateRequest;
import com.ondo.wholesale.product.dto.request.ProductUpdateRequest;
import com.ondo.wholesale.product.dto.response.ProductDetailResponse;
import com.ondo.wholesale.product.dto.request.VariantPriceRequest;
import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ListingVariantRepository;
import com.ondo.wholesale.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상품 쓰기 유스케이스 (MUL-91: 등록).
 *
 * <p>등록은 상품 + 색상옵션 + variant + (선택) 게시글·판매가를 한 트랜잭션에 만든다.
 * 품번은 {@link ProductNumberAllocator}가 wholesaler 행 락으로 직렬화해 발급한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ListingRepository listingRepository;
    private final ListingVariantRepository listingVariantRepository;
    private final ProductOptionValidator productOptionValidator;
    private final VariantUsageChecker variantUsageChecker;
    private final ProductNumberAllocator productNumberAllocator;
    private final CategoryTree categoryTree;
    private final ProductDetailAssembler productDetailAssembler;

    public ProductDetailResponse create(Long wholesalerId, ProductCreateRequest request) {
        categoryTree.requireLeaf(request.categoryId());
        Map<Long, Color> colors = productOptionValidator.validate(request.colorOptions());

        Product product = Product.builder()
                .wholesalerId(wholesalerId)
                .productNumber(productNumberAllocator.next(wholesalerId))
                .name(request.name())
                .categoryId(request.categoryId())
                .build();
        for (ColorOptionRequest optionRequest : request.colorOptions()) {
            ColorOption option = product.addColorOption(colors.get(optionRequest.colorId()));
            for (Size size : optionRequest.sizes()) {
                option.addVariant(size, product.nextVariantSeq());
            }
        }
        productRepository.save(product);

        if (request.listing() != null) {
            createListing(product, request.listing(), true);
        }
        return productDetailAssembler.assemble(product);
    }

    private void createListing(Product product, ListingUpsertRequest request, boolean creating) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                    List.of(new ErrorResponse.FieldError("listing.title", "게시글 제목은 비울 수 없습니다.")));
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
                .collect(java.util.stream.Collectors.toMap(lv -> lv.getId().getVariantId(), lv -> lv));
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

    /** 상품 수정 (MUL-93). 생략 = 무변경. 잠금 조회로 동시 수정·채번 경합을 직렬화한다. */
    public ProductDetailResponse update(Long wholesalerId, Long productId, ProductUpdateRequest request) {
        Product product = productRepository
                .findWithLockByIdAndWholesalerIdAndDeletedAtIsNull(productId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 없거나 접근할 수 없습니다."));

        if (request.name() != null) {
            product.rename(request.name().get().trim());
        }
        if (request.categoryId() != null) {
            categoryTree.requireLeaf(request.categoryId().get());
            product.changeCategory(request.categoryId().get());
        }
        if (request.colorOptions() != null) {
            replaceColorOptions(product, request.colorOptions().get());
        }
        if (request.listing() != null) {
            upsertListing(product, request.listing().get());
        }
        verifyPriceCoverage(product);
        return productDetailAssembler.assemble(product);
    }

    /**
     * 게시 상품의 불변식 — 살아있는 전 variant 는 판매가가 있어야 한다.
     * variantPrices 없이 colorOptions 로 variant 만 추가하는 경우를 여기서 잡는다.
     */
    private void verifyPriceCoverage(Product product) {
        Listing listing = listingRepository.findByProductIdAndDeletedAtIsNull(product.getId()).orElse(null);
        if (listing == null) {
            return;
        }
        Set<Long> priced = listingVariantRepository.findByIdListingId(listing.getId()).stream()
                .map(lv -> lv.getId().getVariantId())
                .collect(java.util.stream.Collectors.toSet());
        boolean uncovered = product.getColorOptions().stream()
                .flatMap(option -> option.getVariants().stream())
                .filter(Variant::isAlive)
                .anyMatch(v -> v.getId() == null || !priced.contains(v.getId()));
        if (uncovered) {
            throw new ApiException(ErrorCode.PRICE_REQUIRED);
        }
    }

    /**
     * colorOptions 전체 교체 diff (MUL-93 2단계). 요청 = 반영 후 전체 상태다.
     *
     * <p>색 추가 → 옵션 신규(단, 뺐던 색은 남은 옵션 행 재사용 — color_option_uk).
     * 사이즈 추가 → 새 variant 행(soft delete 재추가 포함, seq 새로 발급).
     * 색·사이즈 제거 → variant soft delete 만 (옵션 행·가격 행은 D-053 으로 유지).
     * 삭제 후보를 전부 모은 뒤 {@link VariantUsageChecker#ensureDeletable} 일괄 검사를
     * 통과했을 때만 지운다 — 일부만 걸려도 아무것도 안 지워진다.
     */
    private void replaceColorOptions(Product product, List<ColorOptionRequest> requested) {
        Map<Long, Color> colors = productOptionValidator.validate(requested);

        Map<Long, ColorOption> existingByColorId = new HashMap<>();
        product.getColorOptions().forEach(option -> existingByColorId.put(option.getColor().getId(), option));

        List<Variant> deleteCandidates = new ArrayList<>();
        Set<Long> requestedColorIds = new HashSet<>();
        for (ColorOptionRequest optionRequest : requested) {
            requestedColorIds.add(optionRequest.colorId());
            ColorOption option = existingByColorId.get(optionRequest.colorId());
            if (option == null) {
                option = product.addColorOption(colors.get(optionRequest.colorId()));
            }
            Map<Size, Variant> aliveBySize = new HashMap<>();
            option.getVariants().stream().filter(Variant::isAlive)
                    .forEach(v -> aliveBySize.put(v.getSize(), v));
            for (Size size : optionRequest.sizes()) {
                if (!aliveBySize.containsKey(size)) {
                    option.addVariant(size, product.nextVariantSeq());
                }
            }
            aliveBySize.forEach((size, variant) -> {
                if (!optionRequest.sizes().contains(size)) {
                    deleteCandidates.add(variant);
                }
            });
        }
        existingByColorId.forEach((colorId, option) -> {
            if (!requestedColorIds.contains(colorId)) {
                option.getVariants().stream().filter(Variant::isAlive).forEach(deleteCandidates::add);
            }
        });

        if (!deleteCandidates.isEmpty()) {
            variantUsageChecker.ensureDeletable(deleteCandidates.stream().map(Variant::getId).toList());
            deleteCandidates.forEach(Variant::softDelete);
        }
    }

    /** 게시글 upsert — 없으면 생성 분기, 있으면 내부 필드별 "null = 무변경" 갱신. */
    private void upsertListing(Product product, ListingUpsertRequest request) {
        Listing listing = listingRepository.findByProductIdAndDeletedAtIsNull(product.getId()).orElse(null);
        if (listing == null) {
            createListing(product, request, false);
            return;
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        List.of(new ErrorResponse.FieldError("listing.title", "게시글 제목은 비울 수 없습니다.")));
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

    /** description 은 빈 문자열이 "지움"이다 — 저장은 null 로 한다. */
    private String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private String key(Long colorId, Size size) {
        return colorId + "/" + size.name();
    }
}
