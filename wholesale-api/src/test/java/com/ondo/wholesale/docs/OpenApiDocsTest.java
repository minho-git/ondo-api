package com.ondo.wholesale.docs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 문서 인프라 (MUL-81) — 스펙 생성과 data 봉투 보정, 비로그인 접근.
 *
 * <p>문서는 로그인 전에 봐야 하는 자원이라(프론트·소매 개발용) 전 요청을 비인증으로 보낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 스펙의_성공응답은_data봉투로_감싸져_나온다() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode spec = objectMapper.readTree(body);

        // 로그인 응답이 { data: <LoginResponse 스키마> } 로 감싸졌는지 — advice 와 같은 규칙
        JsonNode login200 = spec.at("/paths/~1api~1wholesale~1auth~1login/post/responses/200/content");
        assertThat(login200.isMissingNode()).as("로그인 경로가 스펙에 있어야 한다").isFalse();
        JsonNode loginSchema = login200.iterator().next().get("schema");
        assertThat(loginSchema.get("properties").has("data")).isTrue();

        // 단건: 상품 상세도 { data: <상세 스키마> } 로 감싸졌는지
        JsonNode detail200 = spec.at("/paths/~1api~1wholesale~1products~1{productId}/get/responses/200/content");
        assertThat(detail200.isMissingNode()).as("상품 상세 경로가 스펙에 있어야 한다").isFalse();
        JsonNode detailSchema = detail200.iterator().next().get("schema");
        assertThat(detailSchema.get("properties").has("data")).isTrue();

        // 페이징 목록: 컨트롤러가 ApiResponse 를 직접 반환 — 봉투를 이중으로 씌우지 않아야 한다
        JsonNode list200 = spec.at("/paths/~1api~1wholesale~1products/get/responses/200/content");
        JsonNode listSchema = list200.iterator().next().get("schema");
        String ref = listSchema.has("$ref") ? listSchema.get("$ref").asText() : listSchema.toString();
        assertThat(ref).contains("ApiResponse");
    }

    @Test
    void 문서페이지와_스펙은_비로그인으로_열린다() throws Exception {
        mvc.perform(get("/docs.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
