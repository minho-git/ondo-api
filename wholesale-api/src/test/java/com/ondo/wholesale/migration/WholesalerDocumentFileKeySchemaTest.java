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

/**
 * 서류 파일 컬럼이 file_key 인지 검증 (MUL-68).
 *
 * <p>이 컬럼에 들어가는 값은 절대 URL 이 아니라 저장소 키다 — {@code uploads/2026/09/ab12.jpg}.
 * 도메인이나 버킷이 바뀌어도 저장된 값을 고칠 필요가 없고, 신분증·사업자등록증처럼
 * 개인정보인 파일을 나중에 비공개 버킷 + 서명 URL 로 옮길 때도 설정만 바꾸면 된다.
 *
 * <p>그런데 V1 이 만든 이름은 {@code file_url} 이었다. 이름과 내용이 다른 컬럼은
 * javadoc 을 아무리 달아도 다음 사람이 컬럼명을 먼저 믿는다. 참조하는 코드도 데이터도
 * 아직 없는 지금이 고치기 가장 싸다.
 */
@SpringBootTest
@Transactional
class WholesalerDocumentFileKeySchemaTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 서류_파일_컬럼은_file_key_다() {
        assertThat(컬럼들()).extracting(c -> c.get("column_name"))
                .contains("file_key")
                .doesNotContain("file_url");
    }

    @Test
    void rename_이_길이와_NOT_NULL_을_바꾸지_않았다() {
        assertThat(컬럼들())
                .filteredOn(c -> "file_key".equals(c.get("column_name")))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.get("character_maximum_length")).isEqualTo(500);
                    assertThat(c.get("is_nullable")).isEqualTo("NO");
                });
    }

    private List<Map<String, Object>> 컬럼들() {
        return jdbc.queryForList(
                "select column_name, character_maximum_length, is_nullable"
                        + " from information_schema.columns"
                        + " where table_schema = 'wholesale' and table_name = 'wholesaler_document'");
    }
}
