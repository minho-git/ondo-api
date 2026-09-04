package com.ondo.retail.wholesale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 도매 서버로 나가는 호출 설정 (MUL-88).
 *
 * <p>이 패키지 안의 {@code @HttpExchange} 인터페이스를 전부 찾아 {@code wholesale} 그룹
 * 하나로 묶는다. 그룹이 하나라 <b>주소·타임아웃·(나중에) 인증 헤더를 여기 한 곳에서만</b>
 * 손대면 된다. 상품·미송·주문이 각자 클라이언트를 들고 있으면 MUL-87 에서 인증을 붙일 때
 * 세 군데를 고쳐야 하고 언젠가 한 군데를 빠뜨린다.
 *
 * <p>구현체는 우리가 안 쓴다. 스프링이 앱 뜰 때 인터페이스를 보고 만들어 끼운다 —
 * 주소 조립도 스프링이 하므로 검색어에 특수문자가 섞여도 우리가 실수할 자리가 없다.
 *
 * <p>인증 헤더도 여기서 한 번만 붙인다 (MUL-87). 부르는 쪽 코드는 헤더가 있는지도
 * 모른다 — 나중에 방식이 바뀌어도 어댑터는 안 고친다.
 */
@Configuration
@EnableConfigurationProperties(WholesaleProperties.class)
@ImportHttpServices(group = "wholesale", basePackages = "com.ondo.retail.wholesale")
public class WholesaleHttpConfig {

    /** 도매 {@code GatewaySecretAuthorizationManager} 가 읽는 헤더. 이름이 같아야 한다. */
    private static final String GATEWAY_SECRET_HEADER = "X-Ondo-Gateway-Secret";

    /**
     * 그룹 공통 설정.
     *
     * <p>타임아웃을 반드시 준다. 안 주면 무한 대기라 도매가 응답을 안 주는 동안
     * 소매 톰캣 스레드가 하나씩 잠겨서, 도매 장애가 소매 전체 장애로 번진다.
     */
    @Bean
    RestClientHttpServiceGroupConfigurer wholesaleGroupConfigurer(WholesaleProperties properties) {
        return groups -> groups.filterByName("wholesale")
                .forEachClient((group, builder) -> apply(builder, properties));
    }

    /**
     * 클라이언트 하나에 우리 설정을 입힌다.
     *
     * <p>{@code @Bean} 안에 두지 않고 꺼낸 건 <b>테스트가 같은 코드를 부르게</b> 하려는
     * 것이다. 람다 안에 두면 테스트가 같은 내용을 다시 적어야 하고, 그러면 설정이
     * 망가져도 테스트는 통과한다.
     */
    static void apply(RestClient.Builder builder, WholesaleProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        builder.baseUrl(properties.baseUrl()).requestFactory(factory);

        // 시크릿이 있을 때만 붙인다. 로컬은 도매도 검사를 생략하므로 짝이 맞는다
        if (StringUtils.hasText(properties.secret())) {
            builder.defaultHeader(GATEWAY_SECRET_HEADER, properties.secret());
        }
    }
}
