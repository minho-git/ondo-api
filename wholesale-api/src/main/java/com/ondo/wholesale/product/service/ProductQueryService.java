package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.product.domain.Listing;
import com.ondo.wholesale.product.domain.Product;
import com.ondo.wholesale.product.dto.ProductDetailResponse;
import com.ondo.wholesale.product.dto.ProductSummaryResponse;
import com.ondo.wholesale.product.repository.ListingRepository;
import com.ondo.wholesale.product.repository.ProductRepository;
import com.ondo.wholesale.product.repository.VariantCountRow;
import com.ondo.wholesale.product.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 목록·단건 조회 (MUL-92).
 *
 * <p>목록은 페이지 쿼리 1방 + 배치 2방(variant 집계, 게시 상태)으로 조립한다 —
 * 상품마다 되묻는 N+1 이 없다. 카테고리 경로는 {@link CategoryTree}가 메모리에서 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final ListingRepository listingRepository;
    private final CategoryTree categoryTree;
    private final ProductDetailAssembler productDetailAssembler;

    public ApiResponse<List<ProductSummaryResponse>> list(Long wholesalerId, ProductListQuery query) {
        // Specification.allOf 는 null 요소를 거부한다 — 조건이 있을 때만 담는다
        List<Specification<Product>> conditions = new ArrayList<>(List.of(
                ProductSpecs.ownedBy(wholesalerId), ProductSpecs.notDeleted()));
        if (query.q() != null && !query.q().isBlank()) {
            conditions.add(ProductSpecs.nameLike(query.q()));
        }
        if (query.categoryId() != null) {
            conditions.add(ProductSpecs.categoryIn(categoryTree.subtreeIds(query.categoryId())));
        }
        if (query.from() != null || query.to() != null) {
            conditions.add(ProductSpecs.createdBetween(query.from(), query.to()));
        }
        Specification<Product> spec = Specification.allOf(conditions);

        Page<Product> products = productRepository.findAll(spec,
                PageRequest.of(query.page(), query.size(), query.sort()));

        List<Long> ids = products.getContent().stream().map(Product::getId).toList();
        Map<Long, VariantCountRow> counts = ids.isEmpty() ? Map.of()
                : variantRepository.countAliveByProductIds(ids).stream()
                        .collect(Collectors.toMap(VariantCountRow::productId, Function.identity()));
        Map<Long, Listing> listings = ids.isEmpty() ? Map.of()
                : listingRepository.findByProductIdInAndDeletedAtIsNull(ids).stream()
                        .collect(Collectors.toMap(l -> l.getProduct().getId(), Function.identity()));

        List<ProductSummaryResponse> rows = products.getContent().stream()
                .map(p -> {
                    VariantCountRow count = counts.get(p.getId());
                    Listing listing = listings.get(p.getId());
                    return new ProductSummaryResponse(
                            p.getId(), p.getProductNumber(), p.getName(),
                            categoryTree.pathOf(p.getCategoryId()),
                            listing == null ? null : listing.getStatus(),
                            count == null ? 0 : (int) count.colorCount(),
                            count == null ? 0 : (int) count.variantCount());
                })
                .toList();

        return ApiResponse.paged(rows, new ApiResponse.PageMeta(
                query.page(), query.size(), products.getTotalElements(), products.getTotalPages()));
    }

    public ProductDetailResponse detail(Long wholesalerId, Long productId) {
        Product product = productRepository
                .findByIdAndWholesalerIdAndDeletedAtIsNull(productId, wholesalerId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 없거나 접근할 수 없습니다."));
        return productDetailAssembler.assemble(product);
    }


}
