package com.ondo.retail.listing;

import com.ondo.retail.listing.dto.CategoryResponse;
import com.ondo.retail.listing.dto.FilterOptionsResponse;
import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
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
