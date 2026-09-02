package com.ondo.wholesale.migration;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이메일 소문자 정규화를 DB 가 강제하는지 검증 (MUL-68).
 *
 * <p>티켓은 "소문자 정규화 저장 (로그인도 동일 정규화)" 를 요구한다. 앱에서
 * {@code toLowerCase(Locale.ROOT)} 를 하는 것만으로는, 정규화를 빠뜨린 코드 경로가
 * 하나라도 생기면(로그인 MUL-69 · 재신청 MUL-71) 대소문자만 다른 중복 계정이 조용히 생긴다.
 * {@code wholesaler_email_uk} 는 대소문자를 구분하므로 그걸 못 막는다.
 *
 * <p>그래서 CHECK 제약으로 DB 가 위반을 즉시 드러내게 한다. 정규화를 대신 해주는 게 아니라,
 * 앱이 안 했을 때 조용히 넘어가지 않게 하는 장치다.
 */
@SpringBootTest
@Transactional
class WholesalerEmailLowercaseSchemaTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 대문자가_섞인_이메일은_거부된다() {
        assertThatThrownBy(() -> 도매처를_만든다("Owner@Ondo.test", "1000000001"))
                .hasMessageContaining("wholesaler_email_lower_ck");
    }

    @Test
    void 전부_대문자인_이메일도_거부된다() {
        assertThatThrownBy(() -> 도매처를_만든다("OWNER@ONDO.TEST", "1000000002"))
                .hasMessageContaining("wholesaler_email_lower_ck");
    }

    @Test
    void 소문자_이메일은_통과한다() {
        assertThatCode(() -> 도매처를_만든다("owner@ondo.test", "1000000003"))
                .doesNotThrowAnyException();
    }

    private void 도매처를_만든다(String email, String bizRegNo) {
        jdbc.update(
                "insert into wholesale.wholesaler"
                        + " (email, password_hash, biz_reg_no, biz_name, biz_owner_name)"
                        + " values (?, 'hash', ?, '온도상사', '김대표')",
                email, bizRegNo);
    }
}
