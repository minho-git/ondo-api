package com.ondo.retail.wholesale;

import com.ondo.retail.wholesale.listing.WholesaleListingApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 도매로 나가는 요청에 시크릿 헤더가 실리는지 (MUL-87).
 *
 * <p>붙이는 자리가 설정 한 곳이라 부르는 코드는 헤더를 모른다. 그래서 어댑터
 * 테스트로는 안 잡히고, 여기서 따로 본다.
 */
class WholesaleHttpConfigTest {

    private static final String HEADER = "X-Ondo-Gateway-Secret";
    private static final String BODY = """
            {"data": [], "meta": {"page":0,"size":20,"totalElements":0,"totalPages":0}}""";

    /**
     * 설정이 실제로 부르는 {@code WholesaleHttpConfig.apply()} 를 그대로 태운다.
     * 테스트가 첨부를 흉내내면 설정이 망가져도 통과하므로 그렇게 하지 않는다.
     */
    private static Fixture 세운다(String secret) {
        RestClient.Builder builder = RestClient.builder();

        WholesaleProperties properties = new WholesaleProperties(
                "http://wholesale.test", Duration.ofSeconds(2), Duration.ofSeconds(5), secret);
        WholesaleHttpConfig.apply(builder, properties);   // ← 운영에서 도는 바로 그 코드

        // apply() 가 requestFactory 를 잡으므로 가짜 서버는 그 뒤에 물린다.
        // 순서를 바꾸면 진짜 네트워크로 나간다
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        WholesaleListingApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(WholesaleListingApi.class);
        return new Fixture(server, api);
    }

    private record Fixture(MockRestServiceServer server, WholesaleListingApi api) {}

    @Test
    @DisplayName("시크릿이 있으면 모든 요청에 헤더가 붙는다")
    void 시크릿이_있으면_헤더가_붙는다() {
        Fixture f = 세운다("open-sesame");
        f.server().expect(header(HEADER, "open-sesame"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        f.api().listings(null, null, null, null, null, null, 0, 20);

        f.server().verify();
    }

    @Test
    @DisplayName("시크릿이 비어 있으면 헤더를 안 붙인다 — 로컬")
    void 시크릿이_없으면_헤더가_없다() {
        Fixture f = 세운다("");
        f.server().expect(headerDoesNotExist(HEADER))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        f.api().listings(null, null, null, null, null, null, 0, 20);

        f.server().verify();
    }
}
