package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.ConsentRequest;
import com.ondo.wholesale.auth.dto.DocumentRequest;
import com.ondo.wholesale.auth.dto.SignupRequest;
import com.ondo.wholesale.auth.dto.SignupResponse;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.wholesaler.ConsentType;
import com.ondo.wholesale.wholesaler.DocumentType;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 가입 신청 (MUL-68).
 *
 * <p>여기엔 트랜잭션이 없다. 정책 검증 · 이메일 정규화 · 비밀번호 해싱까지 끝낸 다음
 * {@link WholesalerRegistrar} 에게 저장만 맡긴다. 커넥션을 잡은 채로 하는 일을
 * 최소한으로 줄이기 위해서다.
 *
 * <p>형식 검증은 이미 DTO 애노테이션이 끝냈다. 여기서 보는 건 <b>정책</b>이고,
 * 어긴 항목마다 다른 에러 코드를 낸다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    /** 8~20자 · 영문 · 숫자 · 특수문자를 각각 1자 이상. 공백은 받지 않는다. */
    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,20}$");

    /**
     * UNIQUE 제약 이름. 여기에만 적어두고 다른 데서 문자열로 쓰지 않는다.
     *
     * <p>예외 메시지를 문자열로 뒤지는 건 DB 종속이다. 그래도 이렇게 하는 이유는,
     * 어느 UNIQUE 가 깨졌는지 알아야 EMAIL_DUPLICATED 와 BIZ_REG_NO_DUPLICATED 를
     * 구분할 수 있기 때문이다. DB 를 갈아탈 일이 생기면 이 파일만 고치면 된다.
     */
    private static final String EMAIL_UK = "wholesaler_email_uk";
    private static final String BIZ_REG_NO_UK = "wholesaler_biz_reg_no_uk";

    private final WholesalerRepository wholesalerRepository;
    private final WholesalerRegistrar registrar;
    private final PasswordEncoder passwordEncoder;

    public SignupResponse signup(SignupRequest request) {
        validatePassword(request.password());
        validateConsents(request.consents());
        validateDocuments(request.documents());

        String email = normalizeEmail(request.email());
        rejectIfDuplicated(email, request.bizRegNo());

        // 해싱은 트랜잭션 밖에서. 커넥션을 잡은 채로 CPU 를 돌지 않는다
        String passwordHash = passwordEncoder.encode(request.password());

        try {
            return registrar.register(toWholesaler(request, email, passwordHash));
        } catch (DataIntegrityViolationException e) {
            // 선조회와 INSERT 사이에 다른 요청이 끼어든 경우. UNIQUE 가 마지막 방어선이다
            throw toDuplicateException(e);
        }
    }

    // ── 정책 ────────────────────────────────────────

    private void validatePassword(String password) {
        if (!PASSWORD_POLICY.matcher(password).matches()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATED);
        }
    }

    /**
     * 6종을 전부 받았는지, 필수 3종에 동의했는지 본다.
     *
     * <p>미동의도 행으로 남겨야 해서 6종을 다 받는다 — "안 물어봤다"와
     * "물어봤는데 거절했다"는 다르고, 법적 증빙에서 그 차이가 중요하다.
     */
    private void validateConsents(List<ConsentRequest> consents) {
        Set<ConsentType> received = EnumSet.noneOf(ConsentType.class);
        consents.forEach(consent -> received.add(consent.type()));

        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        for (ConsentType type : ConsentType.values()) {
            if (!received.contains(type)) {
                errors.add(new ErrorResponse.FieldError("consents", type + " 동의 여부를 보내주세요."));
            }
        }
        for (ConsentRequest consent : consents) {
            if (consent.type().isRequired() && !consent.agreed()) {
                errors.add(new ErrorResponse.FieldError("consents", consent.type() + " 은(는) 필수 동의입니다."));
            }
        }

        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.REQUIRED_CONSENT_MISSING,
                    ErrorCode.REQUIRED_CONSENT_MISSING.defaultMessage(), errors);
        }
    }

    /** 같은 종류를 두 번 보내면 잘못된 요청, 필수 서류가 빠지면 서류 누락이다. */
    private void validateDocuments(List<DocumentRequest> documents) {
        Set<DocumentType> received = EnumSet.noneOf(DocumentType.class);
        for (DocumentRequest document : documents) {
            if (!received.add(document.type())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "같은 종류의 서류를 두 번 보냈습니다.",
                        List.of(new ErrorResponse.FieldError(
                                "documents", document.type() + " 이(가) 중복됐습니다.")));
            }
        }

        List<ErrorResponse.FieldError> missing = Arrays.stream(DocumentType.values())
                .filter(DocumentType::isRequired)
                .filter(type -> !received.contains(type))
                .map(type -> new ErrorResponse.FieldError("documents", type + " 을(를) 올려주세요."))
                .toList();

        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.REQUIRED_DOCUMENT_MISSING,
                    ErrorCode.REQUIRED_DOCUMENT_MISSING.defaultMessage(), missing);
        }
    }

    // ── 중복 ────────────────────────────────────────

    /**
     * 정상 경로에서 친절한 코드를 내려고 먼저 본다. 둘 다 중복이면 이메일을 먼저 알린다.
     *
     * <p>이것만으로는 부족하다 — 조회와 INSERT 사이에 다른 요청이 끼어들 수 있고,
     * 인스턴스가 여러 대면 실제로 일어난다. 최종 방어는 DB 의 UNIQUE 다.
     */
    private void rejectIfDuplicated(String email, String bizRegNo) {
        if (wholesalerRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (wholesalerRepository.existsByBizRegNo(bizRegNo)) {
            throw new ApiException(ErrorCode.BIZ_REG_NO_DUPLICATED);
        }
    }

    private RuntimeException toDuplicateException(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());

        if (message.contains(EMAIL_UK)) {
            return new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (message.contains(BIZ_REG_NO_UK)) {
            return new ApiException(ErrorCode.BIZ_REG_NO_DUPLICATED);
        }
        // 우리가 아는 제약이 아니면 삼키지 않는다. 500 으로 드러나는 게 낫다
        return e;
    }

    // ── 조립 ────────────────────────────────────────

    /**
     * 이메일을 소문자로 맞춘다.
     *
     * <p>{@link Locale#ROOT} 를 반드시 준다 — 터키어 로케일에서
     * {@code "I".toLowerCase()} 는 {@code "ı"} 가 되어 서버 설정에 따라 결과가 달라진다.
     */
    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private Wholesaler toWholesaler(SignupRequest request, String email, String passwordHash) {
        Wholesaler wholesaler = Wholesaler.builder()
                .email(email)
                .passwordHash(passwordHash)
                .phone(request.phone())
                .bizRegNo(request.bizRegNo())
                .bizName(request.bizName())
                .bizOwnerName(request.bizOwnerName())
                .storePhone(request.storePhone())
                .storeBuilding(request.storeBuilding())
                .storeUnit(request.storeUnit())
                .bizCategory(request.bizCategory())
                .build();

        request.consents().forEach(consent -> wholesaler.addConsent(consent.type(), consent.agreed()));
        request.documents().forEach(document -> wholesaler.addDocument(document.type(), document.fileKey()));
        return wholesaler;
    }
}
