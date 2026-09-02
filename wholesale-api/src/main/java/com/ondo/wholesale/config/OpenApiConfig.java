package com.ondo.wholesale.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc 스펙 생성 설정 (MUL-81). 결과물은 {@code /v3/api-docs}, 렌더는 {@code /docs.html}(Scalar).
 *
 * <p>성공 응답은 런타임에 {@code ApiResponseBodyAdvice}가 {@code { "data": ... }} 봉투를
 * 씌우지만, springdoc 은 컨트롤러 반환 타입만 보고 스펙을 만들기 때문에 봉투가 스펙에서
 * 빠진다. {@link #dataEnvelopeCustomizer()}가 advice 와 같은 규칙으로 스펙을 보정한다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI wholesaleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("On도 도매 API")
                .version("0.1.0")
                .description("""
                        도매처용 ERP API. 계약 원본은 설계 저장소(mulbora v2)의 api-lite 문서다.

                        **전역 규약** — 성공 응답은 항상 `data` 봉투로 감싼다(204 제외). \
                        페이징 있는 목록만 `meta`(0-base `page`)를 함께 담는다. \
                        에러는 봉투 없이 `code`·`message`·`errors[]`·`traceId` 를 내려주며, \
                        프론트 분기는 `code` 로만 한다. \
                        인증은 로그인(`POST /api/wholesale/auth/login`) 세션 쿠키다."""));
    }

    /**
     * 모든 2xx(204 제외) 응답 스키마를 {@code { data: <원 스키마> }} 로 치환한다.
     *
     * <p>컨트롤러가 {@code ApiResponse} 를 직접 반환하는 경우(페이징 목록)는 스키마에
     * 이미 봉투가 있으므로 건너뛴다 — advice 가 이중으로 감싸지 않는 것과 같은 규칙.
     */
    @Bean
    OpenApiCustomizer dataEnvelopeCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation ->
                            operation.getResponses().forEach((status, response) -> {
                                if (!status.startsWith("2") || "204".equals(status)) {
                                    return;
                                }
                                Content content = response.getContent();
                                if (content == null) {
                                    return;
                                }
                                content.values().forEach(mediaType -> {
                                    Schema<?> original = mediaType.getSchema();
                                    if (original == null || isEnveloped(original, openApi)) {
                                        return;
                                    }
                                    Schema<Object> envelope = new ObjectSchema();
                                    envelope.addProperty("data", original);
                                    mediaType.setSchema(envelope);
                                });
                            })));
        };
    }

    /**
     * 반환 타입이 이미 봉투({@code ResponseEnvelope} 구현)인 경우 — 컴포넌트 스키마를
     * 따라가 {@code data} 프로퍼티가 있으면 봉투로 본다. advice 의 통과 규칙과 짝이다.
     */
    private boolean isEnveloped(Schema<?> schema, OpenAPI openApi) {
        String ref = schema.get$ref();
        if (ref == null || !ref.startsWith("#/components/schemas/")) {
            return false;
        }
        String name = ref.substring("#/components/schemas/".length());
        Schema<?> resolved = openApi.getComponents() == null || openApi.getComponents().getSchemas() == null
                ? null
                : openApi.getComponents().getSchemas().get(name);
        return resolved != null && resolved.getProperties() != null && resolved.getProperties().containsKey("data");
    }
}
