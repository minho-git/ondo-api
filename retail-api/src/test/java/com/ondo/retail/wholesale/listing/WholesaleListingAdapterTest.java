package com.ondo.retail.wholesale.listing;

import com.ondo.retail.listing.dto.ListingDetailResponse;
import com.ondo.retail.listing.dto.ListingSearchCondition;
import com.ondo.retail.listing.dto.ListingSummaryResponse;
import com.ondo.retail.listing.dto.VariantInfo;
import com.ondo.retail.wholesale.WholesaleApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 도매 상품 API 어댑터 (MUL-88).
 *
 * <p>진짜 도매 서버 대신 가짜를 세워 두고 <b>주고받는 모양</b>을 본다. 도매 DB 도
 * 로컬 시드도 안 쓴다 — 여기서 확인할 건 데이터가 아니라 (1) 주소를 어떻게 만드는지
 * (2) 도매 응답을 소매 응답으로 어떻게 옮기는지 (3) 도매가 실패할 때 뭘 하는지다.
 *
 * <p>스프링 설정을 안 태우고 프록시를 직접 만든다. {@code @HttpExchange} 인터페이스가
 * 실제로 만들어내는 URL 을 봐야 해서다 — 설정을 태우면 그 자리가 가려진다.
 */
class WholesaleListingAdapterTest {

    private static final String BASE = "http://wholesale.test";
    private static final String LISTINGS = BASE + "/api/retail-gateway/listings";

    private MockRestServiceServer 도매;
    private WholesaleListingAdapter adapter;

    @BeforeEach
    void 가짜_도매를_세운다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        도매 = MockRestServiceServer.bindTo(builder).build();

        WholesaleListingApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(WholesaleListingApi.class);
        adapter = new WholesaleListingAdapter(api);
    }

    // ── 주소 만들기 ─────────────────────────────────────────────

    @Test
    @DisplayName("한글 검색어가 퍼센트 인코딩돼 나간다")
    void 한글_검색어를_인코딩해서_보낸다() {
        도매.expect(requestTo(LISTINGS + "?q=%EB%B9%88%ED%8B%B0%EC%A7%80&page=0&size=20"))
                .andRespond(withSuccess(빈_목록(), MediaType.APPLICATION_JSON));

        adapter.search(조건("빈티지"), PageRequest.of(0, 20));

        도매.verify();
    }

    @Test
    @DisplayName("특수문자가 섞여도 주소가 안 깨진다 — 숙제 1-2절")
    void 특수문자를_인코딩해서_보낸다() {
        // 손으로 이어 붙이면 % 는 잘못된 이스케이프가 되고 | 는 주소에 못 쓰는 글자다.
        // 그대로 나가면 도매가 400 을 주거나 프록시가 HTML 오류 페이지를 돌려줘서,
        // 우리가 JSON 인 줄 알고 읽다 터진다
        도매.expect(requestTo(containsString("100%25")))
                .andExpect(requestTo(containsString("%7C")))
                .andExpect(requestTo(not(containsString("|"))))
                .andRespond(withSuccess(빈_목록(), MediaType.APPLICATION_JSON));

        adapter.search(조건("100% | drop"), PageRequest.of(0, 20));

        도매.verify();
    }

    @Test
    @DisplayName("안 건 필터는 주소에 아예 안 붙는다")
    void 비어있는_조건은_안_붙인다() {
        도매.expect(requestTo(LISTINGS + "?page=0&size=20"))
                .andRespond(withSuccess(빈_목록(), MediaType.APPLICATION_JSON));

        adapter.search(new ListingSearchCondition(null, null, null, null, null, null), PageRequest.of(0, 20));

        도매.verify();
    }

    // ── 도매 말 → 소매 말 ───────────────────────────────────────

    @Test
    @DisplayName("목록을 소매 카드로 옮기고 전체 개수는 도매 meta 를 따른다")
    void 목록을_소매_카드로_옮긴다() {
        도매.expect(requestTo(containsString("/listings?")))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "listingId": 2001,
                              "title": "빈티지 플라워 셔츠",
                              "wholesaler": { "id": 101, "name": "무드온" },
                              "thumbnailUrl": "cover.jpg",
                              "minSalePrice": 12500,
                              "colorCount": 2,
                              "sizeCount": 3,
                              "isSinglePieceAllowed": false
                            }
                          ],
                          "meta": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
                        }
                        """, MediaType.APPLICATION_JSON));

        Page<ListingSummaryResponse> page = adapter.search(조건(null), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        ListingSummaryResponse card = page.getContent().getFirst();
        assertThat(card.listingId()).isEqualTo(2001L);
        assertThat(card.wholesaler().name()).isEqualTo("무드온");
        assertThat(card.minSalePrice()).isEqualTo(12500);
        // 한 장에 1건만 왔어도 전체는 137건이다. 이걸 놓치면 페이지가 1장으로 보인다
        assertThat(page.getTotalElements()).isEqualTo(137);
    }

    @Test
    @DisplayName("상세의 색상·사이즈 묶음을 그대로 옮긴다")
    void 상세를_소매_상세로_옮긴다() {
        도매.expect(requestTo(LISTINGS + "/2001"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "listingId": 2001,
                            "title": "빈티지 플라워 셔츠",
                            "description": "봄 신상",
                            "productNumber": 1,
                            "isSinglePieceAllowed": false,
                            "minSalePrice": 12500,
                            "maxSalePrice": 13500,
                            "listedVariantCount": 3,
                            "totalVariantCount": 5,
                            "categoryPath": [ {"id":1,"name":"여성"}, {"id":11,"name":"의류"}, {"id":111,"name":"상의"} ],
                            "wholesaler": { "id":101, "name":"무드온", "storeBuilding":"청평화패션몰", "storeUnit":"2층 24호" },
                            "colorOptions": [
                              {
                                "color": {"id":7,"name":"체리레드","hex":"#C0392B","groupName":"레드"},
                                "variants": [ {"id":3001,"size":"S","salePrice":12500,"orderLimit":500} ]
                              }
                            ],
                            "images": [ {"id":5001,"url":"cover.jpg","sortOrder":0} ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ListingDetailResponse detail = adapter.findById(2001L).orElseThrow();

        assertThat(detail.title()).isEqualTo("빈티지 플라워 셔츠");
        assertThat(detail.categoryPath()).extracting(ListingDetailResponse.CategoryNode::name)
                .containsExactly("여성", "의류", "상의");
        assertThat(detail.wholesaler().storeUnit()).isEqualTo("2층 24호");
        assertThat(detail.colorOptions()).hasSize(1);
        assertThat(detail.colorOptions().getFirst().color().groupName()).isEqualTo("레드");
        assertThat(detail.colorOptions().getFirst().variants().getFirst().orderLimit()).isEqualTo(500);
        assertThat(detail.totalVariantCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("도매의 404 는 예외가 아니라 빈 값이다")
    void 도매의_404는_빈값이_된다() {
        도매.expect(requestTo(LISTINGS + "/9999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"RESOURCE_NOT_FOUND","message":"리소스를 찾을 수 없습니다."}"""));

        Optional<ListingDetailResponse> found = adapter.findById(9999L);

        // 없는 것 · 시즌이 끝난 것 · 지워진 것을 도매가 구분 안 하고, 소매도 안 한다
        assertThat(found).isEmpty();
    }

    // ── 옵션 배치 ───────────────────────────────────────────────

    @Test
    @DisplayName("빈 목록이면 도매를 아예 안 부른다")
    void 빈_목록이면_도매를_안_부른다() {
        // expect 를 하나도 안 걸어놨으므로 호출이 있으면 verify 가 터진다
        assertThat(adapter.findVariants(List.of())).isEmpty();
        assertThat(adapter.findVariants(null)).isEmpty();

        도매.verify();
    }

    @Test
    @DisplayName("옵션은 부른 순서대로 담기고 없는 것은 키가 없다")
    void 옵션은_부른_순서대로_담긴다() {
        // 목록은 콤마가 아니라 반복 파라미터로 나간다. 도매가 이 형태를 받는지는
        // RetailGatewayListingApiTest 가 같이 못박아 둔다
        도매.expect(requestTo(containsString("/variants?ids=3011&ids=3001&ids=99999")))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"variantId":3001,"listingId":2001,"title":"빈티지 플라워 셔츠","thumbnailUrl":"a.jpg",
                             "colorName":"체리레드","size":"S","salePrice":12500,"orderLimit":500,
                             "wholesalerId":101,"wholesalerName":"무드온","orderable":true},
                            {"variantId":3011,"listingId":2003,"title":"와이드 데님 팬츠","thumbnailUrl":"b.jpg",
                             "colorName":"스카이","size":"M","salePrice":31000,"orderLimit":0,
                             "wholesalerId":102,"wholesalerName":"라온","orderable":false}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<Long, VariantInfo> variants = adapter.findVariants(List.of(3011L, 3001L, 99999L));

        // 장바구니가 담긴 순서대로 그리므로 도매 응답 순서가 아니라 우리가 부른 순서다
        assertThat(variants.keySet()).containsExactly(3011L, 3001L);
        assertThat(variants).doesNotContainKey(99999L);
        assertThat(variants.get(3011L).orderable()).isFalse();
        assertThat(variants.get(3001L).salePrice()).isEqualTo(12500);
    }

    // ── 도매가 실패할 때 ────────────────────────────────────────

    @Test
    @DisplayName("도매가 5xx 면 소매 예외로 바꿔 던진다")
    void 도매의_5xx는_소매_예외가_된다() {
        도매.expect(requestTo(containsString("/listings?")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.search(조건(null), PageRequest.of(0, 20)))
                .isInstanceOf(WholesaleApiException.class)
                .hasMessageContaining("상품 목록");
    }

    @Test
    @DisplayName("상세도 404 말고 다른 실패는 삼키지 않는다")
    void 상세의_5xx는_빈값이_아니라_예외다() {
        도매.expect(requestTo(LISTINGS + "/2001"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        // 여기서 Optional.empty 를 돌려주면 도매 장애가 "그런 상품 없음" 으로 둔갑한다
        assertThatThrownBy(() -> adapter.findById(2001L))
                .isInstanceOf(WholesaleApiException.class);
    }

    // ── 거들 ────────────────────────────────────────────────────

    private static ListingSearchCondition 조건(String q) {
        return new ListingSearchCondition(q, null, null, null, null, null);
    }

    private static String 빈_목록() {
        return """
                {"data": [], "meta": {"page":0,"size":20,"totalElements":0,"totalPages":0}}""";
    }
}
