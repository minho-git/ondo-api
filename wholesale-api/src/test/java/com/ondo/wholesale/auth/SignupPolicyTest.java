package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.ConsentRequest;
import com.ondo.wholesale.auth.dto.DocumentRequest;
import com.ondo.wholesale.auth.dto.SignupRequest;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.wholesaler.ConsentType;
import com.ondo.wholesale.wholesaler.DocumentType;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 가입 <b>정책</b> 검증 (MUL-68).
 *
 * <p>형식 위반은 DTO 애노테이션이 이미 걸러 {@code VALIDATION_FAILED} 로 나간다.
 * 여기서 보는 건 어긴 항목마다 <b>다른 에러 코드</b>를 내야 하는 것들이다 —
 * 비밀번호 정책 · 필수 동의 · 필수 서류 · 중복.
 *
 * <p>DB 를 띄우지 않는다. 저장은 {@link WholesalerRegistrar} 가 맡고 여기선 가짜를 쓴다.
 */
class SignupPolicyTest {

    private final WholesalerRepository wholesalerRepository = mock(WholesalerRepository.class);
    private final WholesalerRegistrar registrar = mock(WholesalerRegistrar.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final SignupService service =
            new SignupService(wholesalerRepository, registrar, passwordEncoder);

    // ── 비밀번호 정책 ────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "'Abc123!',        7자라_짧다",
            "'Abcdefgh123456789012!', 21자라_길다",
            "'abcdefgh!',      숫자가_없다",
            "'abcdefg123',     특수문자가_없다",
            "'1234567!',       영문이_없다"
    })
    void 비밀번호_정책을_어기면_전용_코드가_나온다(String password, String 이유) {
        assertThatThrownBy(() -> service.signup(요청_비밀번호(password)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATED);

        // 정책에 걸렸으면 저장까지 가지 않는다
        verify(registrar, never()).register(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcd123!", "aB3!aB3!aB3!aB3!aB3!"})
    void 정책에_맞는_비밀번호는_통과한다(String password) {
        service.signup(요청_비밀번호(password));

        verify(registrar).register(any());
    }

    // ── 동의 ────────────────────────────────────────

    @Test
    void 필수_동의를_안_하면_어느_항목인지_알려준다() {
        List<ConsentRequest> 동의 = new ArrayList<>(동의_정상());
        동의.set(0, new ConsentRequest(ConsentType.TERMS, false));

        ApiException thrown = 잡는다(요청_동의(동의));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
        assertThat(thrown.errors()).extracting(ErrorResponse.FieldError::reason)
                .anyMatch(r -> r.contains("TERMS"));
    }

    @Test
    void 동의_6종을_다_안_보내면_누락_목록이_나온다() {
        List<ConsentRequest> 동의 = List.of(
                new ConsentRequest(ConsentType.TERMS, true),
                new ConsentRequest(ConsentType.PRIVACY, true));

        ApiException thrown = 잡는다(요청_동의(동의));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
        assertThat(thrown.errors()).extracting(ErrorResponse.FieldError::reason)
                .anyMatch(r -> r.contains("INFO_CONFIRM"));
    }

    @Test
    void 선택_동의는_거절해도_통과한다() {
        service.signup(요청());

        verify(registrar).register(any());
    }

    // ── 서류 ────────────────────────────────────────

    @Test
    void 필수_서류가_빠지면_어느_서류인지_알려준다() {
        List<DocumentRequest> 서류 = List.of(
                new DocumentRequest(DocumentType.BIZ_REG, "uploads/a.jpg"));

        ApiException thrown = 잡는다(요청_서류(서류));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.REQUIRED_DOCUMENT_MISSING);
        assertThat(thrown.errors()).extracting(ErrorResponse.FieldError::reason)
                .anyMatch(r -> r.contains("CEO_ID"));
    }

    @Test
    void 서류를_아예_안_보내도_필수_서류_누락이다() {
        ApiException thrown = 잡는다(요청_서류(List.of()));

        assertThat(thrown.errorCode()).isEqualTo(ErrorCode.REQUIRED_DOCUMENT_MISSING);
    }

    @Test
    void 같은_종류_서류를_두_번_보내면_잘못된_요청이다() {
        List<DocumentRequest> 서류 = List.of(
                new DocumentRequest(DocumentType.BIZ_REG, "uploads/a.jpg"),
                new DocumentRequest(DocumentType.BIZ_REG, "uploads/b.jpg"),
                new DocumentRequest(DocumentType.CEO_ID, "uploads/c.jpg"));

        assertThat(잡는다(요청_서류(서류)).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 매장사진은_없어도_된다() {
        service.signup(요청());

        verify(registrar).register(any());
    }

    // ── 중복 ────────────────────────────────────────

    @Test
    void 이메일이_이미_있으면_전용_코드가_나온다() {
        when(wholesalerRepository.existsByEmail("owner@ondo.test")).thenReturn(true);

        assertThat(잡는다(요청()).errorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    void 사업자번호가_이미_있으면_전용_코드가_나온다() {
        when(wholesalerRepository.existsByBizRegNo("1234567890")).thenReturn(true);

        assertThat(잡는다(요청()).errorCode()).isEqualTo(ErrorCode.BIZ_REG_NO_DUPLICATED);
    }

    @Test
    void 둘_다_중복이면_이메일을_먼저_알려준다() {
        when(wholesalerRepository.existsByEmail(anyString())).thenReturn(true);
        when(wholesalerRepository.existsByBizRegNo(anyString())).thenReturn(true);

        assertThat(잡는다(요청()).errorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    // ── 정규화 · 해싱 ────────────────────────────────

    @Test
    void 이메일은_소문자로_바꿔_저장한다() {
        service.signup(요청_이메일("Owner@Ondo.TEST"));

        ArgumentCaptor<Wholesaler> captor = ArgumentCaptor.forClass(Wholesaler.class);
        verify(registrar).register(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("owner@ondo.test");
    }

    @Test
    void 중복_확인도_소문자로_한다() {
        // 대문자로 조회하면 이미 가입한 계정을 못 찾는다
        service.signup(요청_이메일("Owner@Ondo.TEST"));

        verify(wholesalerRepository).existsByEmail("owner@ondo.test");
    }

    @Test
    void 비밀번호는_해시로_바꿔_저장한다() {
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("$2a$10$해시");

        service.signup(요청());

        ArgumentCaptor<Wholesaler> captor = ArgumentCaptor.forClass(Wholesaler.class);
        verify(registrar).register(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$해시");
    }

    // ── 도우미 ──────────────────────────────────────

    private ApiException 잡는다(SignupRequest request) {
        return (ApiException) org.assertj.core.api.Assertions
                .catchThrowable(() -> service.signup(request));
    }

    private static List<ConsentRequest> 동의_정상() {
        return List.of(
                new ConsentRequest(ConsentType.TERMS, true),
                new ConsentRequest(ConsentType.PRIVACY, true),
                new ConsentRequest(ConsentType.INFO_CONFIRM, true),
                new ConsentRequest(ConsentType.MARKETING_SMS, false),
                new ConsentRequest(ConsentType.MARKETING_EMAIL, false),
                new ConsentRequest(ConsentType.MARKETING_PUSH, false));
    }

    private static List<DocumentRequest> 서류_정상() {
        return List.of(
                new DocumentRequest(DocumentType.BIZ_REG, "uploads/2026/09/ab12cd34.jpg"),
                new DocumentRequest(DocumentType.CEO_ID, "uploads/2026/09/ef56gh78.jpg"));
    }

    private static SignupRequest 요청() {
        return 요청("owner@ondo.test", "Abcd1234!", 서류_정상(), 동의_정상());
    }

    private static SignupRequest 요청_이메일(String email) {
        return 요청(email, "Abcd1234!", 서류_정상(), 동의_정상());
    }

    private static SignupRequest 요청_비밀번호(String password) {
        return 요청("owner@ondo.test", password, 서류_정상(), 동의_정상());
    }

    private static SignupRequest 요청_서류(List<DocumentRequest> documents) {
        return 요청("owner@ondo.test", "Abcd1234!", documents, 동의_정상());
    }

    private static SignupRequest 요청_동의(List<ConsentRequest> consents) {
        return 요청("owner@ondo.test", "Abcd1234!", 서류_정상(), consents);
    }

    private static SignupRequest 요청(String email, String password,
                                    List<DocumentRequest> documents,
                                    List<ConsentRequest> consents) {
        return new SignupRequest(
                email, password, "01012345678", "1234567890",
                "온도상사", "김대표", "0212345678", "누죤", "3층 C-25", "도매 및 소매업",
                documents, consents);
    }
}
