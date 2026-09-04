package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
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
import com.ondo.wholesale.product.dto.response.ProductDetailResponse;
import com.ondo.wholesale.product.dto.request.VariantPriceRequest;
import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ListingVariantRepository;
import com.ondo.wholesale.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            createListing(product, request.listing());
        }
        return productDetailAssembler.assemble(product);
    }

    private void createListing(Product product, ListingUpsertRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                    List.of(new ErrorResponse.FieldError("listing.title", "게시글 제목은 비울 수 없습니다.")));
        }
        Listing listing = Listing.builder()
                .product(product)
                .title(request.title())
                .description(request.description())
                .singlePieceAllowed(Boolean.TRUE.equals(request.isSinglePieceAllowed()))
                .build();
        listing.replaceImages(request.images() == null ? List.of() : request.images());
        listingRepository.save(listing);

        savePrices(product, listing, request.variantPrices() == null ? List.of() : request.variantPrices());
    }

    /**
     * 판매가 저장 — 등록에선 전 variant 가 신규라 (colorId, size) 지정만 허용한다.
     * 살아있는 전 variant 를 정확히 1건씩 덮어야 한다: 누락 = PRICE_REQUIRED,
     * 없는 조합 지시 = INVARIANT_VIOLATED, 중복·variantId 지정 = VALIDATION_FAILED.
     */
    private void savePrices(Product product, Listing listing, List<VariantPriceRequest> prices) {
        Map<String, Variant> aliveByColorAndSize = new HashMap<>();
        product.getColorOptions().forEach(option -> option.getVariants().stream()
                .filter(Variant::isAlive)
                .forEach(v -> aliveByColorAndSize.put(
                        key(option.getColor().getId(), v.getSize()), v)));

        Map<Long, VariantPriceRequest> covered = new HashMap<>();
        for (VariantPriceRequest price : prices) {
            if (price.variantId() != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        List.of(new ErrorResponse.FieldError("listing.variantPrices.variantId",
                                "등록에서는 variantId 를 지정할 수 없습니다.")));
            }
            if (price.colorId() == null || price.size() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        List.of(new ErrorResponse.FieldError("listing.variantPrices",
                                "colorId 와 size 를 함께 지정해야 합니다.")));
            }
            Variant variant = aliveByColorAndSize.get(key(price.colorId(), price.size()));
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
        covered.forEach((variantId, price) -> listingVariantRepository.save(ListingVariant.builder()
                .listingId(listing.getId()).variantId(variantId)
                .salePrice(price.salePrice()).orderLimit(price.orderLimit())
                .build()));
    }

    private String key(Long colorId, Size size) {
        return colorId + "/" + size.name();
    }
}
