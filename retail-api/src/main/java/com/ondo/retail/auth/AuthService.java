package com.ondo.retail.auth;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.auth.dto.SignUpRequest;
import com.ondo.retail.auth.dto.SignUpResponse;
import com.ondo.retail.retailer.RetailerDocRepository;
import com.ondo.retail.retailer.RetailerPrivateRepository;
import com.ondo.retail.retailer.RetailerRepository;
import com.ondo.retail.retailer.TermsAgreementRepository;
import com.ondo.retail.retailer.domain.RetailerDoc;
import com.ondo.retail.retailer.domain.RetailerPrivate;
import com.ondo.retail.retailer.domain.TermsAgreement;
import com.ondo.retail.retailer.domain.TermsType;
import com.ondo.retail.storage.FileStorage;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import com.ondo.retail.retailer.domain.Retailer;
import com.ondo.retail.auth.dto.LoginRequest;
import com.ondo.retail.auth.dto.RetailerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    /** 사업자등록증으로 받을 수 있는 형식. 명세에 jpg · png · pdf 로 적혀 있다. */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "application/pdf");

    /** 명세상 10MB. 스프링 기본값(1MB)과 달라서 application.yml 도 같이 올려뒀다. */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final RetailerRepository retailerRepository;
    private final RetailerPrivateRepository retailerPrivateRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final RetailerDocRepository retailerDocRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorage fileStorage;

    /**
     * 가입 신청. 계정 · 개인정보 · 약관 동의 · 등록증을 한 트랜잭션에 넣는다.
     *
     * <p>등록증을 따로 받지 않는 이유는 {@code retailer_doc.retailer_id} 가 NOT NULL 이라
     * 파일이 계정보다 먼저 존재할 수 없어서다.
     *
     * <p>세션을 주지 않는다. 가입 직후는 늘 PENDING 이라 그 세션으로 할 수 있는 게 없다.
     */
    @Transactional
    public SignUpResponse signUp(SignUpRequest request, MultipartFile bizLicense) {
        validate(bizLicense);

        if (retailerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Retailer retailer = retailerRepository.save(Retailer.signUp(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.shopName()));

        retailerPrivateRepository.save(RetailerPrivate.of(
                retailer.getId(), request.ownerName(), request.mobile(), request.bizRegNo()));

        List<TermsAgreement> agreements = request.agreedTerms().stream()
                .distinct()
                .map(type -> TermsAgreement.of(retailer.getId(), type))
                .toList();
        termsAgreementRepository.saveAll(agreements);

        String fileUrl = fileStorage.store(bizLicense, "retailer/" + retailer.getId());
        retailerDocRepository.save(RetailerDoc.bizLicense(retailer.getId(), fileUrl));

        log.info("가입 신청. retailerId={} shopName={}", retailer.getId(), retailer.getShopName());
        return SignUpResponse.from(retailer);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    /**
     * 이메일과 비밀번호를 확인하고 계정을 돌려준다. 세션은 컨트롤러가 만든다.
     *
     * <p>승인 여부는 보지 않는다. 승인 안 된 계정도 로그인은 된다 — 승인 대기 · 거절 화면을
     * 봐야 하기 때문이다. 세션은 "누구냐" 고 승인은 "쓸 수 있느냐" 라 서로 상관이 없다.
     */
    public Retailer login(LoginRequest request) {
        Retailer retailer = retailerRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), retailer.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("로그인 성공. retailerId={} status={}", retailer.getId(), retailer.getApprovalStatus());
        return retailer;
    }

    /**
     * 없는 이메일과 틀린 비밀번호를 <b>같은 에러로 묶는 게 중요하다.</b>
     * 나눠서 알려주면 어떤 이메일이 가입돼 있는지 밖에서 확인할 수 있게 된다.
     */
    public RetailerResponse me(Long retailerId) {
        Retailer retailer = retailerRepository.findById(retailerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return RetailerResponse.from(retailer);
    }

    public boolean isEmailAvailable(String email) {
        return !retailerRepository.existsByEmailIgnoreCase(email);
    }
}
