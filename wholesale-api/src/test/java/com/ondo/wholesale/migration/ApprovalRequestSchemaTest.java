package com.ondo.wholesale.migration;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 심사 이력 스키마 검증 (MUL-67).
 *
 * <p>핵심은 "행 = 신청 1라운드" 다. 재신청이 일어나도 이전 라운드의 거절 사유와 문제 서류가
 * 남아야 화면이 "신청 일시" 와 "이번에 문제된 서류" 를 낼 수 있다. 예전처럼 wholesaler 의
 * 컬럼을 덮어쓰면 그 이력이 사라진다.
 */
@SpringBootTest
@Transactional
class ApprovalRequestSchemaTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void approval_request_컬럼이_티켓_명세대로다() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "select column_name, data_type from information_schema.columns"
                        + " where table_schema = 'wholesale' and table_name = 'approval_request'");

        assertThat(columns).extracting(c -> c.get("column_name"))
                .containsExactlyInAnyOrder(
                        "id", "wholesaler_id", "status", "reason",
                        "document_types", "actor", "created_at", "decided_at");

        assertThat(columns)
                .filteredOn(c -> "document_types".equals(c.get("column_name")))
                .singleElement()
                .extracting(c -> c.get("data_type"))
                .isEqualTo("ARRAY");
    }

    @Test
    void 한_도매처의_신청_라운드가_행으로_쌓인다() {
        long wholesalerId = 도매처를_만든다("rounds@ondo.test");

        신청_라운드를_넣는다(wholesalerId, "REJECTED", "사업자등록증이 흐릿합니다", "{BIZ_REG}");
        신청_라운드를_넣는다(wholesalerId, "REJECTED", "대표자 신분증이 만료됐습니다", "{CEO_ID}");
        신청_라운드를_넣는다(wholesalerId, "PENDING", null, "{}");

        List<Map<String, Object>> rounds = jdbc.queryForList(
                "select status, reason from wholesale.approval_request"
                        + " where wholesaler_id = ? order by created_at, id",
                wholesalerId);

        // 최신 행만 남기고 덮어썼다면 3건이 아니라 1건이 된다
        assertThat(rounds).hasSize(3);
        assertThat(rounds).extracting(r -> r.get("reason"))
                .containsExactly("사업자등록증이 흐릿합니다", "대표자 신분증이 만료됐습니다", null);
    }

    @Test
    void 한_도매처에_심사_대기는_하나뿐이다() {
        long wholesalerId = 도매처를_만든다("pending@ondo.test");
        신청_라운드를_넣는다(wholesalerId, "PENDING", null, "{}");

        // 티켓에 없던 제약. 재신청 연타가 라운드를 둘로 벌리는 걸 DB 가 막는다
        assertThatThrownBy(() -> 신청_라운드를_넣는다(wholesalerId, "PENDING", null, "{}"))
                .hasMessageContaining("approval_request_pending_uk");
    }

    @Test
    void 허용_밖_status는_거부된다() {
        long wholesalerId = 도매처를_만든다("badstatus@ondo.test");

        assertThatThrownBy(() -> 신청_라운드를_넣는다(wholesalerId, "REVIEWING", null, "{}"))
                .hasMessageContaining("approval_request_status_ck");
    }

    @Test
    void 허용_밖_document_types는_거부된다() {
        long wholesalerId = 도매처를_만든다("baddoc@ondo.test");

        assertThatThrownBy(() -> 신청_라운드를_넣는다(wholesalerId, "REJECTED", "사유", "{BOGUS}"))
                .hasMessageContaining("approval_request_doc_types_ck");
    }

    @Test
    void wholesaler에서_rejection_reason이_사라졌다() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "select column_name from information_schema.columns"
                        + " where table_schema = 'wholesale' and table_name = 'wholesaler'");

        // 거절 사유는 approval_request 최신 REJECTED 행에서 파생한다. 이중 저장 금지
        assertThat(columns).extracting(c -> c.get("column_name"))
                .doesNotContain("rejection_reason")
                .contains("approval_status");
    }

    private long 도매처를_만든다(String email) {
        return jdbc.queryForObject(
                "insert into wholesale.wholesaler"
                        + " (email, password_hash, biz_reg_no, biz_name, biz_owner_name)"
                        + " values (?, 'hash', ?, '온도상사', '김대표') returning id",
                Long.class,
                email, email.substring(0, 10));
    }

    private void 신청_라운드를_넣는다(long wholesalerId, String status, String reason, String documentTypes) {
        jdbc.update(
                "insert into wholesale.approval_request"
                        + " (wholesaler_id, status, reason, document_types)"
                        + " values (?, ?, ?, ?::text[])",
                wholesalerId, status, reason, documentTypes);
    }
}
