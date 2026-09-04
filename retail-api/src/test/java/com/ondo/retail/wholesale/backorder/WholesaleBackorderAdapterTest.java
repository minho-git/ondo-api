package com.ondo.retail.wholesale.backorder;

import com.ondo.retail.backorder.dto.BackorderLine;
import com.ondo.retail.wholesale.WholesaleApiException;
import java.time.LocalDate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 도매 미송 API 어댑터 (MUL-97).
 *
 * <p>{@code WholesaleListingAdapterTest} 와 같은 방식이다 — 진짜 도매 대신 가짜를 세우고
 * (1) 주소를 어떻게 만드는지 (2) 도매 응답을 소매 말로 어떻게 옮기는지
 * (3) 도매가 실패할 때 뭘 하는지를 본다.
 */
class WholesaleBackorderAdapterTest {

    private static final String BASE = "http://wholesale.test";
    private static final String BACKORDERS = BASE + "/api/retail-gateway/backorders";

    private MockRestServiceServer 도매;
    private WholesaleBackorderAdapter adapter;

    @BeforeEach
    void 가짜_도매를_세운다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        도매 = MockRestServiceServer.bindTo(builder).build();

        WholesaleBackorderApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(WholesaleBackorderApi.class);
        adapter = new WholesaleBackorderAdapter(api);
    }

    @Test
    @DisplayName("소매처 id 와 페이지를 주소에 싣는다")
    void 소매처_id_를_실어_보낸다() {
        도매.expect(requestTo(BACKORDERS + "?retailerId=42&page=1&size=50"))
                .andRespond(withSuccess(빈_목록(), MediaType.APPLICATION_JSON));

        adapter.findWaiting(42L, PageRequest.of(1, 50));

        도매.verify();
    }

    @Test
    @DisplayName("도매 미송을 소매 줄로 옮기고 전체 개수는 도매 meta 를 따른다")
    void 미송을_소매_줄로_옮긴다() {
        도매.expect(requestTo(BACKORDERS + "?retailerId=42&page=0&size=20"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "backorderId": 4402,
                              "retailOrderId": 5001,
                              "orderedAt": "2026-08-30T09:30:00+09:00",
                              "wholesaler": { "id": 11, "name": "코튼클럽" },
                              "listingId": 4380,
                              "title": "베이직 라운드 니트",
                              "colorName": "오트밀",
                              "size": "FREE",
                              "qty": 4,
                              "expectedInboundDate": "2026-09-10",
                              "expectedInboundReason": "공장 재입고 예정"
                            }
                          ],
                          "meta": { "page": 0, "size": 20, "totalElements": 37, "totalPages": 2 }
                        }
                        """, MediaType.APPLICATION_JSON));

        Page<BackorderLine> page = adapter.findWaiting(42L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        BackorderLine line = page.getContent().getFirst();
        assertThat(line.backorderId()).isEqualTo(4402L);
        // 도매는 retailOrderId 라 부르지만 소매에 들어오면 그냥 자기 주문서 id 다
        assertThat(line.orderId()).isEqualTo(5001L);
        assertThat(line.wholesaler().name()).isEqualTo("코튼클럽");
        assertThat(line.title()).isEqualTo("베이직 라운드 니트");
        assertThat(line.size()).isEqualTo("FREE");
        assertThat(line.qty()).isEqualTo(4);
        assertThat(line.expectedInboundDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        // 한 장에 1건만 왔어도 전체는 37건이다
        assertThat(page.getTotalElements()).isEqualTo(37);
    }

    @Test
    @DisplayName("예상 입고일이 없는 줄도 그대로 옮긴다")
    void 예상_입고일이_없어도_옮긴다() {
        도매.expect(requestTo(BACKORDERS + "?retailerId=42&page=0&size=20"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "backorderId": 4408,
                              "retailOrderId": 5008,
                              "orderedAt": "2026-09-01T11:10:00+09:00",
                              "wholesaler": { "id": 3, "name": "무드온" },
                              "listingId": null,
                              "title": "단종된 니트",
                              "colorName": "네이비",
                              "size": "M",
                              "qty": 1,
                              "expectedInboundDate": null,
                              "expectedInboundReason": null
                            }
                          ],
                          "meta": { "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
                        }
                        """, MediaType.APPLICATION_JSON));

        BackorderLine line = adapter.findWaiting(42L, PageRequest.of(0, 20)).getContent().getFirst();

        // 화면이 "도매처가 입고일을 안내할 예정이에요" 를 그리는 분기다
        assertThat(line.expectedInboundDate()).isNull();
        assertThat(line.expectedInboundReason()).isNull();
        // 게시글이 지워졌으면 상품 상세로 못 넘어간다
        assertThat(line.listingId()).isNull();
    }

    @Test
    @DisplayName("도매가 죽으면 소매 예외로 바뀐다")
    void 도매_장애는_소매_예외가_된다() {
        도매.expect(requestTo(BACKORDERS + "?retailerId=42&page=0&size=20"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.findWaiting(42L, PageRequest.of(0, 20)))
                .isInstanceOf(WholesaleApiException.class)
                .hasMessageContaining("미송 목록");
    }

    private static String 빈_목록() {
        return """
                { "data": [], "meta": { "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
                """;
    }
}
