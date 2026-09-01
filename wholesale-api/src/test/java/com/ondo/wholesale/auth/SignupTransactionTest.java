package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.ConsentRequest;
import com.ondo.wholesale.auth.dto.DocumentRequest;
import com.ondo.wholesale.auth.dto.SignupRequest;
import com.ondo.wholesale.auth.dto.SignupResponse;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.support.PostgresTestSupport;
import com.ondo.wholesale.wholesaler.ApprovalRequestRepository;
import com.ondo.wholesale.wholesaler.ConsentType;
import com.ondo.wholesale.wholesaler.DocumentType;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * 가입이 진짜로 한 트랜잭션인지 검증 (MUL-68).
 *
 * <p><b>클래스에 {@code @Transactional} 을 붙이지 않았다.</b> 붙이면 테스트가 만든
 * 트랜잭션이 서비스의 커밋을 삼켜버려서, 정작 확인하려는 두 가지를 볼 수 없다 —
 * 중간에 실패했을 때 앞의 저장이 되돌려지는지, 그리고 UNIQUE 위반이 커밋 시점에
 * 어떻게 튀는지. 대신 매 테스트 뒤에 직접 지운다.
 */
@SpringBootTest
class SignupTransactionTest extends PostgresTestSupport {

    @Autowired
    private SignupService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private WholesalerRepository wholesalerRepository;

    @MockitoSpyBean
    private ApprovalRequestRepository approvalRequestRepository;

    @AfterEach
    void 지운다() {
        jdbc.execute("delete from wholesale.approval_request");
        jdbc.execute("delete from wholesale.wholesaler_consent");
        jdbc.execute("delete from wholesale.wholesaler_document");
        jdbc.execute("delete from wholesale.wholesaler");
    }

    @Test
    void 가입_한_번에_네_가지가_저장된다() {
        service.signup(요청("owner@ondo.test", "1234567890"));

        assertThat(행수("wholesaler")).isEqualTo(1);
        assertThat(행수("wholesaler_consent")).isEqualTo(6);
        assertThat(행수("wholesaler_document")).isEqualTo(2);
        assertThat(행수("approval_request")).isEqualTo(1);
    }

    @Test
    void 응답은_티켓이_적은_네_필드다() {
        SignupResponse response = service.signup(요청("owner@ondo.test", "1234567890"));

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(response.bizName()).isEqualTo("온도상사");
        assertThat(response.bizRegNo()).isEqualTo("1234567890");
        // 신청 일시는 approval_request 행의 created_at 이다
        assertThat(response.appliedAt()).isNotNull();
    }

    @Test
    void 비밀번호는_평문으로_저장되지_않는다() {
        service.signup(요청("owner@ondo.test", "1234567890"));

        String hash = jdbc.queryForObject(
                "select password_hash from wholesale.wholesaler", String.class);
        assertThat(hash).isNotEqualTo("Abcd1234!").startsWith("$2");
    }

    @Test
    void 마지막_저장이_실패하면_앞의_것도_전부_사라진다() {
        doThrow(new RuntimeException("심사 신청 저장 실패"))
                .when(approvalRequestRepository).save(any());

        assertThatThrownBy(() -> service.signup(요청("rollback@ondo.test", "1234567890")))
                .isInstanceOf(RuntimeException.class);

        // 도매처와 동의 · 서류는 이미 INSERT 됐지만 커밋되지 않아야 한다
        assertThat(행수("wholesaler")).isZero();
        assertThat(행수("wholesaler_consent")).isZero();
        assertThat(행수("wholesaler_document")).isZero();
        assertThat(행수("approval_request")).isZero();
    }

    @Test
    void 선조회를_지나쳐도_이메일_UNIQUE_가_막는다() {
        이미_가입된_계정("dup@ondo.test", "1000000001");
        // 동시 요청 둘이 선조회를 나란히 통과한 상황을 흉내낸다.
        // 인스턴스가 여러 대면 실제로 일어나는 일이고, 이때 UNIQUE 가 마지막 방어선이다
        doReturn(false).when(wholesalerRepository).existsByEmail(anyString());

        ApiException thrown = catchThrowableOfType(ApiException.class,
                () -> service.signup(요청("dup@ondo.test", "2000000002")));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
        assertThat(행수("wholesaler")).isEqualTo(1);
    }

    @Test
    void 선조회를_지나쳐도_사업자번호_UNIQUE_가_막는다() {
        이미_가입된_계정("first@ondo.test", "1000000001");
        doReturn(false).when(wholesalerRepository).existsByBizRegNo(anyString());

        ApiException thrown = catchThrowableOfType(ApiException.class,
                () -> service.signup(요청("second@ondo.test", "1000000001")));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.BIZ_REG_NO_DUPLICATED);
        assertThat(행수("wholesaler")).isEqualTo(1);
    }

    // ── 도우미 ──────────────────────────────────────

    private int 행수(String table) {
        return jdbc.queryForObject("select count(*) from wholesale." + table, Integer.class);
    }

    private void 이미_가입된_계정(String email, String bizRegNo) {
        jdbc.update("insert into wholesale.wholesaler"
                        + " (email, password_hash, biz_reg_no, biz_name, biz_owner_name)"
                        + " values (?, 'hash', ?, '먼저상사', '박대표')",
                email, bizRegNo);
    }

    private static SignupRequest 요청(String email, String bizRegNo) {
        return new SignupRequest(
                email, "Abcd1234!", "01012345678", bizRegNo,
                "온도상사", "김대표", "0212345678", "누죤", "3층 C-25", "도매 및 소매업",
                List.of(new DocumentRequest(DocumentType.BIZ_REG, "uploads/2026/09/ab12cd34.jpg"),
                        new DocumentRequest(DocumentType.CEO_ID, "uploads/2026/09/ef56gh78.jpg")),
                List.of(new ConsentRequest(ConsentType.TERMS, true),
                        new ConsentRequest(ConsentType.PRIVACY, true),
                        new ConsentRequest(ConsentType.INFO_CONFIRM, true),
                        new ConsentRequest(ConsentType.MARKETING_SMS, false),
                        new ConsentRequest(ConsentType.MARKETING_EMAIL, false),
                        new ConsentRequest(ConsentType.MARKETING_PUSH, false)));
    }
}
