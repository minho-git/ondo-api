package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.LoginRequest;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.text.EmailNormalizer;
import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 로그인 자격 대조 (MUL-69).
 *
 * <p>여기까지가 "누구냐"다. 세션을 만드는 일은 여기서 하지 않는다.
 *
 * <p>승인 상태는 <b>보지 않는다.</b> PENDING·REJECTED 계정도 로그인은 된다 —
 * 심사 현황 화면을 봐야 하기 때문이다. 승인은 "무엇을 할 수 있느냐"라 다른 질문이고,
 * 그건 {@code ApprovedAuthorizationManager} 가 보호 API 앞에서 본다.
 *
 * <p>{@code @Transactional} 을 붙이지 않았다. 조회 한 번뿐이라 트랜잭션이 필요 없고,
 * 느린 BCrypt 대조를 커넥션을 쥔 채로 돌리지 않으려는 뜻도 있다({@code SignupService} 와 같은 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    /**
     * 계정을 못 찾았을 때 대신 대조할 해시. 아무도 모르는 값(랜덤 24바이트)의 BCrypt 결과다.
     *
     * <p>계정이 없다고 바로 던지면 응답이 눈에 띄게 빨라진다. BCrypt 는 무차별 대입을
     * 막으려고 <b>일부러 느리게</b> 만든 계산이라, 건너뛴 경로는 수십 배 빠르다. 그러면
     * 응답 내용이 같아도 <b>걸린 시간</b>으로 가입 여부가 새어나간다.
     * 그래서 없는 계정도 같은 무게의 계산을 한 번 돌리고 결과를 버린다.
     */
    private static final String DUMMY_HASH = "$2a$10$R.9QPGQG04Hzi8l7CMMSduF.E7rm4Sb8wodMTQ.gXhewoqqJuIr7e";

    private final WholesalerRepository wholesalerRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 이메일·비밀번호를 대조하고 세션에 담을 주체를 돌려준다.
     *
     * <p>실패는 <b>이유를 나누지 않는다.</b> 없는 이메일이든 틀린 비밀번호든 같은
     * {@link ErrorCode#LOGIN_FAILED} 다 — 나눠 알려주면 밖에서 이메일만 바꿔 넣어보며
     * 가입 여부를 알아낼 수 있다.
     */
    public WholesalePrincipal authenticate(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        Optional<Wholesaler> found = wholesalerRepository.findByEmail(email);

        // 계정이 없어도 대조는 한다 — 위 DUMMY_HASH 주석 참고
        String hash = found.map(Wholesaler::getPasswordHash).orElse(DUMMY_HASH);
        boolean matched = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !matched) {
            // 어떤 이메일로 실패했는지는 남기지 않는다. 로그가 곧 가입자 명단이 된다.
            log.info("로그인 실패");
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }

        Wholesaler wholesaler = found.get();
        log.info("로그인 성공. wholesalerId={} status={}", wholesaler.getId(), wholesaler.getApprovalStatus());
        return new WholesalePrincipal(
                wholesaler.getId(), wholesaler.getEmail(), wholesaler.getApprovalStatus());
    }
}
