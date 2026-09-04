package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.master.domain.Color;
import com.ondo.wholesale.master.dto.CategoryPathItem;
import com.ondo.wholesale.master.repository.CategoryRepository;
import com.ondo.wholesale.master.repository.ColorRepository;
import com.ondo.wholesale.master.service.CategoryQueryService;
import com.ondo.wholesale.master.service.CategoryTree;
import com.ondo.wholesale.master.dto.CategoryNodeResponse;
import com.ondo.wholesale.product.domain.Size;
import com.ondo.wholesale.retailgateway.dto.RetailCategoryResponse;
import com.ondo.wholesale.retailgateway.dto.RetailFilterOptionsResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingDetailResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingSearchCondition;
import com.ondo.wholesale.retailgateway.dto.RetailListingSummaryResponse;
import com.ondo.wholesale.retailgateway.dto.RetailVariantInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소매 상품 조회 조립 (MUL-88).
 *
 * <p>{@link RetailGatewayListingQuery} 가 가져온 평평한 행을 화면 모양으로 접는다.
 * 상세 하나에 쿼리가 여러 방 나가는데(뼈대·색상별 옵션·이미지), 한 방으로 묶으면
 * 이미지 수 × 옵션 수만큼 행이 불어난다. 나눠 받아서 여기서 합친다.
 *
 * <p><b>카테고리·색상은 도매 화면과 같은 것을 쓴다</b> (MUL-90). 그쪽에 이미
 * {@code master} 패키지가 있어서 여기서 또 쿼리하면 같은 걸 두 벌 갖게 되고,
 * 채빈이 조건을 하나 더 걸면 소매만 안 따라가 두 화면이 다른 트리를 보여준다.
 * 다만 응답 DTO 는 우리 것을 쓴다 — 소매는 {@code depth} 를 안 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetailGatewayListingService {

    private final RetailGatewayListingQuery query;
    private final CategoryQueryService categoryQueryService;
    private final CategoryTree categoryTree;
    private final CategoryRepository categoryRepository;
    private final ColorRepository colorRepository;

    /**
     * 목록 · 검색.
     *
     * <p>id 를 먼저 뽑고 카드 값을 따로 가져오는데, {@code IN} 은 순서를 보장하지 않는다.
     * 그래서 여기서 id 순서대로 다시 세운다 — 안 그러면 최신순 정렬이 무너진다.
     */
    public Paged<RetailListingSummaryResponse> search(RetailListingSearchCondition condition, int page, int size) {
        List<Long> ids = query.searchIds(condition, page, size);
        if (ids.isEmpty()) {
            // 빈 장이라도 전체 개수는 알려줘야 소매가 "마지막 장 다음" 인지 "결과 없음" 인지 안다
            return new Paged<>(List.of(), query.countSearch(condition));
        }

        Map<Long, RetailListingSummaryResponse> byId = query.cards(ids).stream()
                .collect(Collectors.toMap(RetailListingSummaryResponse::listingId, Function.identity()));

        List<RetailListingSummaryResponse> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return new Paged<>(ordered, query.countSearch(condition));
    }

    /**
     * 상세.
     *
     * @throws ResourceNotFoundException 없거나 · 게시 중이 아니거나 · 지워졌을 때.
     *         셋을 구분하지 않는다 — 도매가 시즌을 닫은 건지 원래 없는 건지 소매가 알 이유가 없다
     */
    public RetailListingDetailResponse detail(long listingId) {
        RetailGatewayListingQuery.ListingHeader header = query.findHeader(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("게시글을 찾을 수 없습니다."));

        return new RetailListingDetailResponse(
                header.listingId(),
                header.title(),
                header.description(),
                header.productNumber(),
                header.isSinglePieceAllowed(),
                header.minSalePrice(),
                header.maxSalePrice(),
                header.listedVariantCount(),
                header.totalVariantCount(),
                categoryPath(header.categoryId()),
                header.wholesaler(),
                toColorOptions(query.colorVariants(listingId)),
                query.images(listingId));
    }

    /**
     * 카테고리 3단 트리.
     *
     * <p>도매 화면용 {@link CategoryQueryService} 가 만든 트리를 소매 모양으로 옮긴다.
     * 다른 건 {@code depth} 하나뿐이다 — 소매는 중첩 구조만 쓴다.
     */
    public List<RetailCategoryResponse> categories() {
        return categoryQueryService.tree().stream().map(RetailGatewayListingService::toRetailNode).toList();
    }

    /** 필터 사이드바. 색상·사이즈는 마스터 전체, 가격만 실제 값이다. */
    public RetailFilterOptionsResponse filterOptions() {
        // 도매 화면과 같은 색상 마스터를 쓴다. 쿼리가 그룹 순 → 그룹 안 순서로 정렬해 준다
        Map<Long, List<RetailFilterOptionsResponse.Color>> colorsByGroup = new LinkedHashMap<>();
        Map<Long, String> groupNames = new LinkedHashMap<>();

        for (Color color : colorRepository.findAllWithGroupOrdered()) {
            Long groupId = color.getGroup().getId();
            groupNames.putIfAbsent(groupId, color.getGroup().getName());
            colorsByGroup.computeIfAbsent(groupId, k -> new ArrayList<>())
                    .add(new RetailFilterOptionsResponse.Color(color.getId(), color.getName(), color.getHex()));
        }

        List<RetailFilterOptionsResponse.ColorGroup> colorGroups = groupNames.entrySet().stream()
                .map(e -> new RetailFilterOptionsResponse.ColorGroup(
                        e.getKey(), e.getValue(), colorsByGroup.get(e.getKey())))
                .toList();

        // 사이즈 마스터 테이블이 없다. 도매 화면과 같은 enum 을 쓴다 —
        // DB CHECK(variant_size_ck)와 두 곳이 같아야 해서 하나를 늘리면 둘 다 손대야 한다
        List<String> sizes = java.util.Arrays.stream(Size.values()).map(Size::label).toList();

        return new RetailFilterOptionsResponse(colorGroups, sizes, query.priceRange());
    }

    /** 옵션 배치 조회. 소매 장바구니가 쓴다. */
    public List<RetailVariantInfoResponse> variants(List<Long> variantIds) {
        return query.variants(variantIds);
    }

    /**
     * 색상 × 사이즈로 펼쳐진 행을 색상별로 접는다.
     *
     * <p>쿼리가 이미 그룹 순 → 색상 순 → 사이즈 순으로 정렬해서 준다.
     * {@link LinkedHashMap} 이 그 순서를 그대로 지킨다.
     */
    private static List<RetailListingDetailResponse.ColorOption> toColorOptions(
            List<RetailGatewayListingQuery.ColorVariantRow> rows) {

        Map<Long, List<RetailGatewayListingQuery.ColorVariantRow>> byColor = new LinkedHashMap<>();
        for (RetailGatewayListingQuery.ColorVariantRow row : rows) {
            byColor.computeIfAbsent(row.colorId(), k -> new ArrayList<>()).add(row);
        }

        return byColor.values().stream().map(group -> {
            RetailGatewayListingQuery.ColorVariantRow first = group.getFirst();
            List<RetailListingDetailResponse.Variant> variants = group.stream()
                    .map(r -> new RetailListingDetailResponse.Variant(
                            r.variantId(), r.size(), r.salePrice(), r.orderLimit()))
                    .toList();
            return new RetailListingDetailResponse.ColorOption(
                    new RetailListingDetailResponse.Color(
                            first.colorId(), first.colorName(), first.hex(), first.groupName()),
                    first.imageUrl(),
                    variants);
        }).toList();
    }

    /** 도매 화면용 노드를 소매 노드로. depth 를 떨어뜨리는 것이 전부다. */
    private static RetailCategoryResponse toRetailNode(CategoryNodeResponse source) {
        return new RetailCategoryResponse(
                source.id(),
                source.name(),
                source.children().stream().map(RetailGatewayListingService::toRetailNode).toList());
    }

    /** 리프에서 루트까지의 경로. 도매 화면과 같은 것을 쓴다. */
    private List<RetailListingDetailResponse.CategoryNode> categoryPath(Long categoryId) {
        return categoryTree.pathOf(categoryId).stream()
                .map(item -> new RetailListingDetailResponse.CategoryNode(item.id(), item.name()))
                .toList();
    }

    /**
     * 한 장과 전체 개수. 컨트롤러가 {@code meta} 를 만드는 데 쓴다.
     *
     * <p>스프링의 {@code Page} 를 안 쓴 건 그게 JSON 으로 나갈 물건이 아니어서다 —
     * 여기서는 컨트롤러에 넘기는 중간 모양일 뿐이다.
     */
    public record Paged<T>(List<T> content, long totalElements) {}
}
