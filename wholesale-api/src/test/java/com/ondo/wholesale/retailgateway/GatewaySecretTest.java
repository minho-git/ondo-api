package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소매 접점의 시크릿 헤더 검사 (MUL-87).
 *
 * <p>여기서 잡고 싶은 사고는 둘이다. 하나는 <b>시크릿 없이도 열리는 것</b> —
 * 내부 ALB 를 붙이면 VPC 안에서 아무나 부를 수 있게 되므로 반드시 막혀야 한다.
 * 다른 하나는 <b>로컬이 깨지는 것</b> — 팀원들이 시크릿을 맞추지 않아도 돌아가야 한다.
 */
class GatewaySecretTest {

    private static final String BODY = """
            {
              "retailOrderId": 90210, "retailerId": 77, "wholesalerId": 12,
              "expectedPaymentMethod": "CASH", "receiveBy": "AGENT",
              "items": [ { "variantId": 1042, "qty": 3, "expectedUnitPrice": 5000 } ]
            }
            """;

    @Nested
    @DisplayName("시크릿이 설정돼 있을 때 — 배포 환경")
    @WebMvcTest(RetailGatewayOrderController.class)
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
            ApprovedAuthorizationManager.class,
            ErrorResponseWriter.class, ApiResponseBodyAdvice.class, TraceIdFilter.class})
    @TestPropertySource(properties = "ondo.gateway.secret=open-sesame")
    class 시크릿이_있을_때 {

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("맞는 시크릿을 실으면 통과한다")
        void 맞는_시크릿은_통과한다() throws Exception {
            mvc.perform(post("/api/retail-gateway/orders")
                            .header(GatewaySecretAuthorizationManager.HEADER, "open-sesame")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("헤더가 아예 없으면 401 이다")
        void 헤더가_없으면_401이다() throws Exception {
            mvc.perform(post("/api/retail-gateway/orders")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("틀린 시크릿은 401 이다")
        void 틀린_시크릿은_401이다() throws Exception {
            mvc.perform(post("/api/retail-gateway/orders")
                            .header(GatewaySecretAuthorizationManager.HEADER, "wrong-value")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("앞부분만 맞아도 401 이다 — 한 글자씩 맞춰 나갈 수 없다")
        void 앞부분만_맞으면_401이다() throws Exception {
            mvc.perform(post("/api/retail-gateway/orders")
                            .header(GatewaySecretAuthorizationManager.HEADER, "open-ses")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("시크릿에 ASCII 밖 글자가 있으면")
    class 시크릿_글자_제한 {

        @Test
        @DisplayName("한글 시크릿은 만들 때 막는다 — 헤더에 못 싣는 값이다")
        void 한글_시크릿은_거부한다() {
            // 그냥 두면 소매가 보낸 헤더가 깨져서 401 이 아니라 400 이 오고,
            // "시크릿이 틀렸나" 를 한참 헤매게 된다
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> new GatewaySecretAuthorizationManager("열려라참깨"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ASCII");
        }
    }

    @Nested
    @DisplayName("시크릿이 비어 있을 때 — 로컬 개발")
    @WebMvcTest(RetailGatewayOrderController.class)
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
            ApprovedAuthorizationManager.class,
            ErrorResponseWriter.class, ApiResponseBodyAdvice.class, TraceIdFilter.class})
    @TestPropertySource(properties = "ondo.gateway.secret=")
    class 시크릿이_없을_때 {

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("헤더 없이도 통과한다 — 팀원이 시크릿을 안 맞춰도 로컬이 돌아간다")
        void 헤더_없이도_통과한다() throws Exception {
            mvc.perform(post("/api/retail-gateway/orders")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
        }
    }
}
