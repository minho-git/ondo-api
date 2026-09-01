package com.ondo.wholesale.auth.dto;

import com.ondo.wholesale.wholesaler.ConsentType;
import com.ondo.wholesale.wholesaler.DocumentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 요청의 <b>형식</b> 검증 (MUL-68).
 *
 * <p>여기서 걸리는 건 전부 {@code VALIDATION_FAILED} 가 된다. 비밀번호 정책 ·
 * 필수 동의 · 필수 서류처럼 전용 에러 코드를 내야 하는 <b>정책</b> 검증은
 * 서비스가 맡으므로 이 테스트에 없다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다 — Validator 만 직접 쓴다.
 */
class SignupRequestValidationTest {

    private static final List<DocumentRequest> 서류_정상 = List.of(
            new DocumentRequest(DocumentType.BIZ_REG, "uploads/2026/09/ab12cd34.jpg"),
            new DocumentRequest(DocumentType.CEO_ID, "uploads/2026/09/ef56gh78.jpg"));

    private static final List<ConsentRequest> 동의_정상 = List.of(
            new ConsentRequest(ConsentType.TERMS, true),
            new ConsentRequest(ConsentType.PRIVACY, true),
            new ConsentRequest(ConsentType.INFO_CONFIRM, true),
            new ConsentRequest(ConsentType.MARKETING_SMS, false),
            new ConsentRequest(ConsentType.MARKETING_EMAIL, false),
            new ConsentRequest(ConsentType.MARKETING_PUSH, false));

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void 검증기를_연다() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void 검증기를_닫는다() {
        factory.close();
    }

    @Test
    void 제대로_채운_요청은_걸리지_않는다() {
        assertThat(validator.validate(요청())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ondo.test", "owner@", "@ondo.test", "owner ondo.test", " "})
    void 이메일_형식이_아니면_걸린다(String email) {
        assertThat(위반_필드들(요청(email, "1234567890", "온도상사", 서류_정상, 동의_정상)))
                .contains("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "123-45-67890", "abcdefghij"})
    void 사업자번호가_숫자_10자리가_아니면_걸린다(String bizRegNo) {
        // 티켓이 "정확히 숫자 10자리" 라고 못박았다. 하이픈은 받지 않는다
        assertThat(위반_필드들(요청("owner@ondo.test", bizRegNo, "온도상사", 서류_정상, 동의_정상)))
                .contains("bizRegNo");
    }

    @Test
    void 상호가_비면_걸린다() {
        assertThat(위반_필드들(요청("owner@ondo.test", "1234567890", "", 서류_정상, 동의_정상)))
                .contains("bizName");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://cdn.ddmondo.co.kr/uploads/a.jpg",   // 절대 URL 은 받지 않는다
            "../../etc/passwd",                          // 상위 경로 탈출
            "documents/a.jpg",                           // uploads/ 로 시작해야 한다
            "uploads/a b.jpg"                            // 공백
    })
    void 서류_키가_형식에_맞지_않으면_걸린다(String fileKey) {
        List<DocumentRequest> 서류 = List.of(new DocumentRequest(DocumentType.BIZ_REG, fileKey));

        assertThat(위반_필드들(요청("owner@ondo.test", "1234567890", "온도상사", 서류, 동의_정상)))
                .anyMatch(f -> f.contains("fileKey"));
    }

    @Test
    void 동의_항목에_agreed_가_없으면_걸린다() {
        // 원시 boolean 이었다면 조용히 false(미동의) 가 됐을 자리다
        List<ConsentRequest> 동의 = List.of(new ConsentRequest(ConsentType.TERMS, null));

        assertThat(위반_필드들(요청("owner@ondo.test", "1234567890", "온도상사", 서류_정상, 동의)))
                .anyMatch(f -> f.contains("agreed"));
    }

    private Set<String> 위반_필드들(SignupRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static SignupRequest 요청() {
        return 요청("owner@ondo.test", "1234567890", "온도상사", 서류_정상, 동의_정상);
    }

    private static SignupRequest 요청(String email, String bizRegNo, String bizName,
                                    List<DocumentRequest> documents,
                                    List<ConsentRequest> consents) {
        return new SignupRequest(
                email, "Abcd1234!", "01012345678", bizRegNo,
                bizName, "김대표", "0212345678", "누죤", "3층 C-25", "도매 및 소매업",
                documents, consents);
    }
}
