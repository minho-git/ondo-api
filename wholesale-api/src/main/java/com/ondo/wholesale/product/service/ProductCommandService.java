package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.master.service.CategoryTree;
import com.ondo.wholesale.product.domain.ColorOption;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.product.dto.request.ColorOptionRequest;
import com.ondo.wholesale.product.dto.request.ProductCreateRequest;
import com.ondo.wholesale.product.dto.request.ProductUpdateRequest;
import com.ondo.wholesale.product.dto.response.ProductDetailResponse;
import com.ondo.wholesale.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 상품 쓰기 유스케이스 (MUL-91 등록 · MUL-93 수정). 흐름만 여기 있고,
 * 게시·가격 규칙은 {@link ListingWriter}, 옵션 전체 교체는 {@link ColorOptionDiffer}가 맡는다.
 *
 * <p>등록은 상품 + 색상옵션 + variant + (선택) 게시글·판매가를 한 트랜잭션에 만든다.
 * 품번은 {@link ProductNumberAllocator}가 wholesaler 행 락으로 직렬화해 발급한다.
 * 수정·삭제는 잠금 조회로 동시 수정·variant_seq 채번 경합을 직렬화한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final com.ondo.wholesale.product.repository.ListingRepository listingRepository;
    private final VariantUsageChecker variantUsageChecker;
    private final ProductOptionValidator productOptionValidator;
    private final ProductNumberAllocator productNumberAllocator;
    private final CategoryTree categoryTree;
    private final ListingWriter listingWriter;
    private final ColorOptionDiffer colorOptionDiffer;
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
            listingWriter.create(product, request.listing(), true);
        }
        return productDetailAssembler.assemble(product);
    }

    /** 상품 수정 (MUL-93). 생략 = 무변경. */
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
            colorOptionDiffer.replace(product, request.colorOptions().get());
        }
        if (request.listing() != null) {
            listingWriter.upsert(product, request.listing().get());
        }
        listingWriter.verifyPriceCoverage(product);
        return productDetailAssembler.assemble(product);
    }

    /**
     * 상품 삭제 (MUL-94) — 게시글 동반 soft delete, 품번은 영구 결번(D-004).
     * 살아있는 전 variant 가 삭제 가능해야 통과한다(일괄 409) — 일부만 걸려도 아무것도 안 지운다.
     * 지우는 건 살아있는 variant 뿐이다 — 이미 죽은 variant 의 삭제 시각은 보존한다.
     */
    public void delete(Long wholesalerId, Long productId) {
        Product product = productRepository
                .findWithLockByIdAndWholesalerIdAndDeletedAtIsNull(productId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 없거나 접근할 수 없습니다."));

        java.util.List<com.ondo.wholesale.product.domain.Variant> alive = product.getColorOptions().stream()
                .flatMap(option -> option.getVariants().stream())
                .filter(com.ondo.wholesale.product.domain.Variant::isAlive)
                .toList();
        if (!alive.isEmpty()) {
            variantUsageChecker.ensureDeletable(alive.stream()
                    .map(com.ondo.wholesale.product.domain.Variant::getId).toList());
            alive.forEach(com.ondo.wholesale.product.domain.Variant::softDelete);
        }
        listingRepository.findByProductIdAndDeletedAtIsNull(product.getId())
                .ifPresent(com.ondo.wholesale.product.domain.Listing::softDelete);
        product.softDelete();
    }
}
