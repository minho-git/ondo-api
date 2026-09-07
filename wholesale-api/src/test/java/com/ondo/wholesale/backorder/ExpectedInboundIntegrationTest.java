package com.ondo.wholesale.backorder;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예상 입고일 등록(전체 대체) API 통합 검증 (MUL-48).
 *
 * <p>SKU 에 붙는 값이라 미송과 독립이다. PUT 이 값 2개를 통째로 대체한다 —
 * 날짜만 바뀌고 예전 사유가 남는 상태를 계약이 허용하지 않는다. 이력은 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpectedInboundIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long 티셔츠;

    @BeforeEach
    void SKU를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "expected-inbound@ondo.test", "9500000045");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9189);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9286, 9287);
        티셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "티셔츠", 7);
        jdbc.update("""
                update wholesale.variant
                set expected_inbound_date = '2024-01-10', expected_inbound_reason = '예전 사유'
                where id = ?
                """, 티셔츠);
    }

    @Test
    void 날짜와_사유를_통째로_대체하고_저장값을_돌려준다() throws Exception {
        mvc.perform(등록(티셔츠, """
                        { "expectedInboundDate": "2026-09-20", "expectedInboundReason": "공장 생산 일정이 3일 밀려요." }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variantId").value(티셔츠))
                .andExpect(jsonPath("$.data.productNumber").value(7))
                .andExpect(jsonPath("$.data.variantNumber").value(1))
                .andExpect(jsonPath("$.data.expectedInboundDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.expectedInboundReason").value("공장 생산 일정이 3일 밀려요."));

        assertThat(jdbc.queryForObject("select expected_inbound_date from wholesale.variant where id = "
                + 티셔츠, LocalDate.class)).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(jdbc.queryForObject("select expected_inbound_reason from wholesale.variant where id = "
                + 티셔츠, String.class)).isEqualTo("공장 생산 일정이 3일 밀려요.");
    }

    @Test
    void null_날짜는_해제이고_사유도_함께_null이_된다() throws Exception {
        mvc.perform(등록(티셔츠, "{ \"expectedInboundDate\": null, \"expectedInboundReason\": \"남길 사유\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedInboundDate").isEmpty())
                .andExpect(jsonPath("$.data.expectedInboundReason").isEmpty());

        assertThat(jdbc.queryForObject("select expected_inbound_date from wholesale.variant where id = "
                + 티셔츠, LocalDate.class)).isNull();
        assertThat(jdbc.queryForObject("select expected_inbound_reason from wholesale.variant where id = "
                + 티셔츠, String.class)).isNull();
    }

    @Test
    void 과거_날짜를_막지_않는다() throws Exception {
        mvc.perform(등록(티셔츠, "{ \"expectedInboundDate\": \"2020-01-01\", \"expectedInboundReason\": null }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedInboundDate").value("2020-01-01"));
    }

    @Test
    void 사유가_200자를_넘으면_400이다() throws Exception {
        String 사유201자 = "가".repeat(201);
        mvc.perform(등록(티셔츠, "{ \"expectedInboundDate\": \"2026-09-20\", \"expectedInboundReason\": \"%s\" }"
                        .formatted(사유201자)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedInboundReason"));
    }

    @Test
    void 삭제된_variant는_404다() throws Exception {
        jdbc.update("update wholesale.variant set deleted_at = now() where id = ?", 티셔츠);

        mvc.perform(등록(티셔츠, "{ \"expectedInboundDate\": \"2026-09-20\", \"expectedInboundReason\": null }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder 등록(long variantId, String body) {
        return put("/api/wholesale/variants/" + variantId + "/expected-inbound")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
