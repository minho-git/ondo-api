package com.ondo.retail.listing;

import com.ondo.retail.listing.dto.CategoryResponse;
import com.ondo.retail.listing.dto.FilterOptionsResponse;
import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
import com.ondo.retail.listing.dto.VariantInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 가짜 상품 데이터. 프론트가 화면을 붙일 수 있게 먼저 낸다.
 *
 * <p><b>임시다.</b> 도매 DB 가 비어 있고 내부 API 도 아직 없어서 지금은 진짜로 못 만든다.
 * 도매 시드와 {@code /api/internal/**} 이 준비되면 이 클래스를 지우고 실제 구현체로 바꾼다.
 * 클래스 이름에 Mock 을 박아둔 건 그때 검색으로 찾으려는 것이다.
 *
 * <p>응답 모양은 노션 명세와 정확히 같다. 그래야 갈아끼울 때 프론트가 안 고친다.
 * 필터는 실제로 걸러주지 않는다 — 모양 확인용이다.
 */
@Component
public class MockListingClient implements ListingClient {

    private static final List<ListingSummaryResponse> LISTINGS = List.of(
            summary(4410L, "빈티지 플라워 셔츠", 3L, "무드온", 12500, 3, 5, false),
            summary(4411L, "루즈핏 니트 가디건", 3L, "무드온", 23000, 5, 4, true),
            summary(4412L, "와이드 데님 팬츠", 9L, "라온", 31000, 2, 6, false),
            summary(4413L, "코튼 반팔 티셔츠", 9L, "라온", 8900, 7, 5, true),
            summary(4414L, "린넨 셋업 자켓", 12L, "코튼클럽", 45000, 2, 4, false));

    @Override
    public Page<ListingSummaryResponse> search(ListingSearchCondition condition, Pageable pageable) {
        return new PageImpl<>(LISTINGS, pageable, LISTINGS.size());
    }

    @Override
    public Optional<ListingDetailResponse> findById(Long listingId) {
        if (LISTINGS.stream().noneMatch(it -> it.listingId().equals(listingId))) {
            return Optional.empty();
        }
        return Optional.of(detail(listingId));
    }

    @Override
    public List<CategoryResponse> categories() {
        return List.of(
                new CategoryResponse(1L, "여성", List.of(
                        new CategoryResponse(11L, "의류", List.of(
                                new CategoryResponse(111L, "상의", List.of()),
                                new CategoryResponse(112L, "하의", List.of()),
                                new CategoryResponse(113L, "아우터", List.of()))),
                        new CategoryResponse(12L, "잡화", List.of(
                                new CategoryResponse(121L, "가방", List.of()))))),
                new CategoryResponse(2L, "남성", List.of(
                        new CategoryResponse(21L, "의류", List.of(
                                new CategoryResponse(211L, "상의", List.of()),
                                new CategoryResponse(212L, "하의", List.of()))))));
    }

    @Override
    public FilterOptionsResponse filterOptions() {
        return new FilterOptionsResponse(
                List.of(
                        new FilterOptionsResponse.ColorGroup(1L, "블랙", List.of(
                                new FilterOptionsResponse.Color(1L, "블랙", "#111111"))),
                        new FilterOptionsResponse.ColorGroup(2L, "레드", List.of(
                                new FilterOptionsResponse.Color(7L, "체리레드", "#C0392B"),
                                new FilterOptionsResponse.Color(8L, "코랄", "#E8746A"))),
                        new FilterOptionsResponse.ColorGroup(3L, "블루", List.of(
                                new FilterOptionsResponse.Color(12L, "네이비", "#2C3E7B"),
                                new FilterOptionsResponse.Color(13L, "스카이", "#7FB3D5")))),
                List.of("XS", "S", "M", "L", "XL", "2XL", "FREE"),
                new FilterOptionsResponse.PriceRange(3000, 89000));
    }

    /**
     * 옵션 정보. 상품 상세의 목과 같은 값을 쓴다.
     *
     * <p>90299 는 일부러 "주문 불가" 로 뒀다 — 담아둔 사이에 도매가 게시를 내린 경우를
     * 프론트가 회색 처리로 그려볼 수 있게 하려는 것이다.
     */
    private static final Map<Long, VariantInfo> VARIANTS = variants();

    @Override
    public Map<Long, VariantInfo> findVariants(List<Long> variantIds) {
        Map<Long, VariantInfo> found = new LinkedHashMap<>();
        for (Long id : variantIds) {
            VariantInfo info = VARIANTS.get(id);
            if (info != null) {
                found.put(id, info);
            }
        }
        return found;
    }

    private static Map<Long, VariantInfo> variants() {
        Map<Long, VariantInfo> map = new LinkedHashMap<>();
        map.put(90231L, variant(90231L, 4410L, "빈티지 플라워 셔츠", "체리레드", "S", 12500, 500, 3L, "무드온", true));
        map.put(90232L, variant(90232L, 4410L, "빈티지 플라워 셔츠", "체리레드", "M", 12500, 500, 3L, "무드온", true));
        map.put(90233L, variant(90233L, 4410L, "빈티지 플라워 셔츠", "체리레드", "L", 13500, 0, 3L, "무드온", true));
        map.put(90234L, variant(90234L, 4410L, "빈티지 플라워 셔츠", "네이비", "S", 12500, 500, 3L, "무드온", true));
        map.put(90241L, variant(90241L, 4411L, "루즈핏 니트 가디건", "블랙", "FREE", 23000, 5, 3L, "무드온", true));
        map.put(90251L, variant(90251L, 4412L, "와이드 데님 팬츠", "네이비", "M", 31000, 0, 9L, "라온", true));
        map.put(90261L, variant(90261L, 4413L, "코튼 반팔 티셔츠", "화이트", "L", 8900, 0, 9L, "라온", true));
        map.put(90271L, variant(90271L, 4414L, "린넨 셋업 자켓", "베이지", "M", 45000, 0, 12L, "코튼클럽", true));
        map.put(90299L, variant(90299L, 4415L, "시즌 종료 원피스", "블랙", "M", 19000, 0, 12L, "코튼클럽", false));
        return map;
    }

    private static VariantInfo variant(Long id, Long listingId, String title, String color, String size,
                                       int price, int limit, Long wsId, String wsName, boolean orderable) {
        return new VariantInfo(id, listingId, title,
                "https://cdn.ondo.test/listings/" + listingId + "/1.jpg",
                color, size, price, limit, wsId, wsName, orderable);
    }

    private static ListingSummaryResponse summary(Long id, String title, Long wsId, String wsName,
                                                  int minPrice, int colors, int sizes, boolean single) {
        return new ListingSummaryResponse(id, title,
                new ListingSummaryResponse.WholesalerBrief(wsId, wsName),
                "https://cdn.ondo.test/listings/" + id + "/1.jpg",
                minPrice, colors, sizes, single);
    }

    private static ListingDetailResponse detail(Long listingId) {
        return new ListingDetailResponse(
                listingId,
                "빈티지 플라워 셔츠",
                "봄 신상. 부드러운 레이온 혼방.",
                1,
                false,
                12500,
                13500,
                5,
                15,
                List.of(
                        new ListingDetailResponse.CategoryNode(1L, "여성"),
                        new ListingDetailResponse.CategoryNode(11L, "의류"),
                        new ListingDetailResponse.CategoryNode(111L, "상의")),
                new ListingDetailResponse.Wholesaler(3L, "무드온", "청평화패션몰", "2층 24호"),
                List.of(
                        new ListingDetailResponse.ColorOption(
                                new ListingDetailResponse.Color(7L, "체리레드", "#C0392B", "레드"),
                                "https://cdn.ondo.test/listings/" + listingId + "/red.jpg",
                                List.of(
                                        new ListingDetailResponse.Variant(90231L, "S", 12500, 500),
                                        new ListingDetailResponse.Variant(90232L, "M", 12500, 500),
                                        new ListingDetailResponse.Variant(90233L, "L", 13500, 0))),
                        new ListingDetailResponse.ColorOption(
                                new ListingDetailResponse.Color(12L, "네이비", "#2C3E7B", "블루"),
                                "https://cdn.ondo.test/listings/" + listingId + "/navy.jpg",
                                List.of(
                                        new ListingDetailResponse.Variant(90234L, "S", 12500, 500),
                                        new ListingDetailResponse.Variant(90235L, "M", 12500, 500)))),
                List.of(
                        new ListingDetailResponse.Image(8801L,
                                "https://cdn.ondo.test/listings/" + listingId + "/1.jpg", 0),
                        new ListingDetailResponse.Image(8802L,
                                "https://cdn.ondo.test/listings/" + listingId + "/2.jpg", 1)));
    }
}
