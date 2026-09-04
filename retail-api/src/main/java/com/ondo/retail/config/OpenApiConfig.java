package com.ondo.retail.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 프론트가 보는 API 문서.
 *
 * <p>세션 쿠키 인증이라는 걸 알려줘야 스웨거 화면에서 로그인 후 그대로 테스트할 수 있다.
 * 안 적어두면 「Try it out」 이 전부 401 로 떨어진다.
 */
@Configuration
public class OpenApiConfig {

    private static final String SESSION_COOKIE = "SESSION_RETAIL";

    /** 사이드바에 뜨는 순서다. 화면 흐름대로 둔다 — 가입하고, 고르고, 담고, 주문한다. */
    private static final List<String> TAG_ORDER =
            List.of("인증 · 가입", "상품", "장바구니", "주문", "미송");

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    /**
     * springdoc 이 컨트롤러에서 찾은 태그를 합치면서 순서가 흐트러진다.
     * 문서를 다 만든 다음에 다시 세워야 우리가 적은 순서대로 나온다.
     */
    @Bean
    public OpenApiCustomizer tagOrder() {
        return openApi -> openApi.getTags()
                .sort(Comparator.comparingInt(t -> {
                    int i = TAG_ORDER.indexOf(t.getName());
                    return i < 0 ? TAG_ORDER.size() : i;
                }));
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("On도마켓 소매 API")
                        .version("v1")
                        .description("""
                                동대문 소매처가 여러 도매처에 한 번에 주문하는 서비스의 소매 API.

                                ### 인증
                                `POST /api/retail/auth/login` 으로 로그인하면 `SESSION_RETAIL` 쿠키가 붙는다.
                                브라우저가 이후 요청에 자동으로 실어 보낸다.

                                승인되지 않은 계정도 **로그인은 된다.** 승인 대기 화면을 봐야 하기 때문이다.
                                다만 `/auth/me` 와 `/auth/logout` 을 뺀 나머지는 `403 ACCOUNT_NOT_APPROVED` 다.

                                ### 응답 규약
                                성공은 `data` 로 감싼다. 목록은 `meta` 가 붙는다.
                                ```
                                { "data": { ... } }
                                { "data": [ ... ], "meta": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "hasNext": true } }
                                ```
                                실패는 봉투 없이 4xx/5xx 와 함께 온다. 배열은 `null` 이 아니라 `[]` 다.
                                ```
                                { "code": "ACCOUNT_NOT_APPROVED", "message": "승인 후 이용할 수 있어요", "traceId": null, "errors": [] }
                                ```
                                `code` 로 분기하고 `message` 는 화면에 그대로 써도 된다.
                                입력값 검증이 걸리면 `errors` 에 필드별로 담긴다.

                                ### 아직 목 데이터인 것
                                상품과 미송은 도매 데이터라 지금은 가짜 값을 돌려준다.
                                **응답 모양은 확정이라** 나중에 진짜로 바뀌어도 프론트는 고칠 게 없다.
                                """))
                .tags(List.of(
                        tag("인증 · 가입", "회원가입 · 로그인 · 내 정보. 로그인하면 SESSION_RETAIL 쿠키가 붙는다."),
                        tag("상품", "도매 상품을 둘러본다."),
                        tag("장바구니", "여러 도매처 상품을 한 바구니에 담는다. 주문할 때 도매처별로 쪼개진다."),
                        tag("주문", "주문서 · 접수 · 내역 · 상세 · 취소. 지금은 껍데기다 — 응답 모양만 낸다."),
                        tag("미송", "주문했는데 아직 못 받은 것. 도매가 풀고 소매는 읽기만 한다.")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE))
                .components(new Components().addSecuritySchemes(SESSION_COOKIE,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SESSION_COOKIE)
                                .description("로그인하면 자동으로 붙는다. 직접 넣을 일은 없다.")));
    }
}
