package com.ondo.retail.wholesale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
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
 */
@Configuration
@EnableConfigurationProperties(WholesaleProperties.class)
@ImportHttpServices(group = "wholesale", basePackages = "com.ondo.retail.wholesale")
public class WholesaleHttpConfig {

    /**
     * 그룹 공통 설정.
     *
     * <p>타임아웃을 반드시 준다. 안 주면 무한 대기라 도매가 응답을 안 주는 동안
     * 소매 톰캣 스레드가 하나씩 잠겨서, 도매 장애가 소매 전체 장애로 번진다.
     */
    @Bean
    RestClientHttpServiceGroupConfigurer wholesaleGroupConfigurer(WholesaleProperties properties) {
        return groups -> groups.filterByName("wholesale").forEachClient((group, builder) -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.connectTimeout());
            factory.setReadTimeout(properties.readTimeout());
            builder.baseUrl(properties.baseUrl()).requestFactory(factory);
        });
    }
}
