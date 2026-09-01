package com.ondo.retail.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondo.retail.auth.dto.LoginRequest;
import com.ondo.retail.auth.dto.SignUpRequest;
import com.ondo.retail.auth.dto.SignUpResponse;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.retailer.RetailerDocRepository;
import com.ondo.retail.retailer.RetailerPrivateRepository;
import com.ondo.retail.retailer.TermsAgreementRepository;
import com.ondo.retail.retailer.domain.ApprovalStatus;
import com.ondo.retail.retailer.domain.Retailer;
import com.ondo.retail.retailer.domain.TermsType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 계정(V2__seed_dev.sql)을 전제로 한다.
 *
 * <p>업로드 경로를 임시 디렉터리로 돌린다. 안 그러면 테스트가 var/uploads 에 파일을 쌓는데,
 * DB 는 롤백돼도 파일은 남는다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "ondo.storage.local-root=${java.io.tmpdir}/ondo-test-uploads")
class AuthServiceTest {

    private static final String SEED_APPROVED = "bombom@ondo.test";
    private static final String SEED_PASSWORD = "ondo1234!";

    @Autowired AuthService authService;
    @Autowired RetailerPrivateRepository retailerPrivateRepository;
    @Autowired TermsAgreementRepository termsAgreementRepository;
    @Autowired RetailerDocRepository retailerDocRepository;

    @Test
    @DisplayName("시드 계정으로 로그인한다")
    void 로그인_성공() {
        Retailer retailer = authService.login(new LoginRequest(SEED_APPROVED, SEED_PASSWORD));

        assertThat(retailer.getEmail()).isEqualTo(SEED_APPROVED);
        assertThat(retailer.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("이메일 대소문자를 구분하지 않는다 — 유니크 인덱스가 lower(email) 이다")
    void 로그인_대소문자() {
        Retailer retailer = authService.login(new LoginRequest("BomBom@ONDO.test", SEED_PASSWORD));

        assertThat(retailer.getEmail()).isEqualTo(SEED_APPROVED);
    }

    @Test
    @DisplayName("승인 안 된 계정도 로그인은 된다 — 승인 대기 화면을 봐야 한다")
    void 로그인_PENDING() {
        Retailer retailer = authService.login(new LoginRequest("pending@ondo.test", SEED_PASSWORD));

        assertThat(retailer.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(retailer.isApproved()).isFalse();
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호가 같은 에러다 — 나누면 가입 여부가 샌다")
    void 로그인_실패는_구분되지_않는다() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("없는사람@ondo.test", SEED_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        assertThatThrownBy(() -> authService.login(new LoginRequest(SEED_APPROVED, "틀린비번")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("가입하면 네 테이블에 한 번에 들어간다")
    void 가입_성공() {
        SignUpResponse response = authService.signUp(요청("test-signup@ondo.test"), 등록증());

        assertThat(response.retailerId()).isNotNull();
        assertThat(response.approvalStatus()).isEqualTo("PENDING");
        assertThat(response.appliedAt()).isNotNull();

        Long id = response.retailerId();
        assertThat(retailerPrivateRepository.findById(id)).isPresent();
        assertThat(termsAgreementRepository.findAll())
                .filteredOn(it -> it.getRetailerId().equals(id))
                .hasSize(2);
        assertThat(retailerDocRepository.findAll())
                .filteredOn(it -> it.getRetailerId().equals(id))
                .singleElement()
                .satisfies(doc -> assertThat(doc.isCurrent()).isTrue());
    }

    @Test
    @DisplayName("이미 가입된 이메일은 대소문자가 달라도 막힌다")
    void 가입_중복_이메일() {
        assertThatThrownBy(() -> authService.signUp(요청("BOMBOM@ondo.test"), 등록증()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("jpg · png · pdf 가 아니면 받지 않는다")
    void 가입_파일_형식() {
        MockMultipartFile 텍스트 = new MockMultipartFile(
                "bizLicense", "a.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        assertThatThrownBy(() -> authService.signUp(요청("file-type@ondo.test"), 텍스트))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("10MB 를 넘으면 받지 않는다")
    void 가입_파일_크기() {
        MockMultipartFile 큰파일 = new MockMultipartFile(
                "bizLicense", "big.png", MediaType.IMAGE_PNG_VALUE, new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> authService.signUp(요청("file-size@ondo.test"), 큰파일))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("가입한 이메일은 사용 불가로 나온다")
    void 이메일_중복_확인() {
        assertThat(authService.isEmailAvailable(SEED_APPROVED)).isFalse();
        assertThat(authService.isEmailAvailable("BomBom@ONDO.test")).isFalse();
        assertThat(authService.isEmailAvailable("아무도안쓴@ondo.test")).isTrue();
    }

    private SignUpRequest 요청(String email) {
        return new SignUpRequest(email, "ondo1234!", "새싹상회", "박새싹",
                "01011112222", "1112223333", List.of(TermsType.SERVICE, TermsType.PRIVACY));
    }

    private MockMultipartFile 등록증() {
        return new MockMultipartFile(
                "bizLicense", "biz.png", MediaType.IMAGE_PNG_VALUE, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    }
}
