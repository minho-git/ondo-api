package com.ondo.retail.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 실패 응답을 문서에 붙인다.
 *
 * <p>springdoc 은 성공 응답만 문서에 넣는다. 컨트롤러 반환 타입에는 성공만 적혀 있고
 * 실패는 GlobalExceptionHandler 가 따로 처리하기 때문이다. 그래서 프론트가 문서에서
 * <b>401 · 403 · 400 의 모양을 못 본다</b> — 화면에서 제일 자주 마주치는 응답인데.
 *
 * <p>엔드포인트마다 공통 실패를 여기서 붙인다. 대신 <b>실제로 날 수 있는 것만</b> 붙인다.
 * 로그인·가입에 401 을 적어두면 문서가 거짓말이 되고, 그러면 없느니만 못하다.
 */
@Configuration
public class ErrorResponseDocs {

    /** 인증이 필요 없는 자리. SecurityConfig 의 PUBLIC_PATHS 와 같은 목록이다. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/retail/auth/sign-up",
            "/api/retail/auth/login",
            "/api/retail/auth/email-availability");

    /** 로그인만 되면 되는 자리. 승인 전에도 불러야 해서 403 이 안 난다. */
    private static final List<String> AUTHENTICATED_ONLY_PATHS = List.of(
            "/api/retail/auth/me",
            "/api/retail/auth/logout");

    private static final String REF = "#/components/schemas/ErrorResponse";

    private final ObjectMapper objectMapper;

    public ErrorResponseDocs(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public OpenApiCustomizer errorResponses() {
        return openApi -> {
            // ErrorResponse 를 반환 타입으로 쓰는 곳이 없어서 스키마가 자동으로 안 잡힌다.
            openApi.schema("ErrorResponse", errorSchema())
                   .schema("FieldError", fieldErrorSchema());

            openApi.getPaths().forEach((path, item) -> item.readOperations()
                    .forEach(operation -> attach(operation, path)));
        };
    }

    private void attach(Operation operation, String path) {
        ApiResponses responses = operation.getResponses();

        if (takesInput(operation)) {
            put(responses, "400", "입력값이 규칙에 맞지 않는다. 필드 단위 검증이면 errors 에 담긴다", """
                {
                  "code": "VALIDATION_FAILED",
                  "message": "입력한 내용을 다시 확인해주세요",
                  "traceId": null,
                  "errors": [
                    { "field": "email", "code": "NOT_BLANK", "message": "이메일을 입력해주세요" }
                  ]
                }""");
        }

        if (!PUBLIC_PATHS.contains(path)) {
            put(responses, "401", "로그인이 안 됐거나 세션이 만료됐다", """
                    { "code": "UNAUTHORIZED", "message": "로그인이 필요해요", "traceId": null, "errors": [] }""");
        }
        if (!PUBLIC_PATHS.contains(path) && !AUTHENTICATED_ONLY_PATHS.contains(path)) {
            put(responses, "403", "로그인은 됐지만 아직 승인 전이다. 승인 대기 화면으로 보낸다", """
                    { "code": "ACCOUNT_NOT_APPROVED", "message": "승인 후 이용할 수 있어요", "traceId": null, "errors": [] }""");
        }

        put(responses, "500", "서버 문제. 사용자에게 원인을 알려주지 않는다", """
                { "code": "INTERNAL_ERROR", "message": "잠시 후 다시 시도해주세요", "traceId": null, "errors": [] }""");
    }

    /**
     * 받는 값이 있나. <b>없으면 400 을 안 붙인다.</b>
     *
     * <p>400 은 "입력이 틀렸다" 는 뜻이라 입력이 있어야 성립한다. 로그아웃처럼 파라미터도
     * 본문도 없는 자리는 틀릴 게 없어서 400 이 날 수가 없다. 실제로 모르는 쿼리를 붙여도
     * 스프링이 그냥 무시하고 200 을 낸다.
     *
     * <p>없는 응답을 적어두면 프론트가 있지도 않은 분기를 만든다. 문서가 실제와 다르면
     * 없느니만 못하다.
     */
    private static boolean takesInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    /** 이미 문서에 적힌 상태코드는 건드리지 않는다. 개별 애노테이션이 항상 우선이다. */
    private void put(ApiResponses responses, String status, String description, String example) {
        if (responses.containsKey(status)) {
            return;
        }
        responses.addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType()
                                .schema(new Schema<>().$ref(REF))
                                .example(objectMapper.readValue(example, Map.class)))));
    }

    /** 실패 응답 본문. 네 키가 항상 같은 자리에 온다. */
    private static Schema<?> errorSchema() {
        return new Schema<>()
                .type("object")
                .description("실패 응답. 성공과 달리 data 봉투가 없다 — 성공·실패는 HTTP status 로 가른다.")
                .addProperty("code", str("이걸로 분기한다. 대문자_스네이크", "ACCOUNT_NOT_APPROVED"))
                .addProperty("message", str("화면에 그대로 써도 되는 문구", "승인 후 이용할 수 있어요"))
                .addProperty("traceId", str("문의가 들어왔을 때 로그를 찾는 값. 지금은 항상 null", null)
                        .nullable(true))
                .addProperty("errors", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/FieldError"))
                        .description("필드 단위 검증 에러. 없으면 빈 배열이다 — null 로 내리지 않는다"))
                .required(List.of("code", "message", "traceId", "errors"));
    }

    /** 어느 입력칸이 왜 걸렸는지. 필드 단위 검증 에러가 있을 때만 찬다. */
    private static Schema<?> fieldErrorSchema() {
        return new Schema<>()
                .type("object")
                .description("입력칸 하나에 대한 에러. 그 칸 아래에 message 를 그대로 붙이면 된다.")
                .addProperty("field", str("JS 경로 표기. 중첩이면 items[2].quantity", "email"))
                .addProperty("code", str("필드 단위 코드. 칸마다 분기할 수 있게", "NOT_BLANK"))
                .addProperty("message", str("그 칸 아래에 그대로 붙는 문구", "이메일을 입력해주세요"))
                .required(List.of("field", "code", "message"));
    }

    private static StringSchema str(String description, String example) {
        StringSchema schema = new StringSchema();
        schema.description(description);
        if (example != null) {
            schema.example(example);
        }
        return schema;
    }
}
