package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.LoginRequest;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인 자격 대조 (MUL-69).
 *
 * <p>DB 를 띄우지 않는다. 여기서 보는 건 <b>규칙</b>이다 — 무엇을 실패로 볼지,
 * 실패를 어떻게 답할지, 승인 상태가 로그인을 막는지.
 *
 * <p>세션을 만드는 일은 이 서비스가 하지 않는다(커밋 4). 여기서는 "누구인지"까지만
 * 확정해 {@link WholesalePrincipal} 로 돌려준다.
 */
class LoginServiceTest {

    private static final String 비밀번호 = "ondo1234!";
    private static final String 해시 = "$2a$10$해시";

    private final WholesalerRepository wholesalerRepository = mock(WholesalerRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final LoginService service = new LoginService(wholesalerRepository, passwordEncoder);

    /**
     * <b>AC 2.</b> 없는 이메일과 틀린 비밀번호가 <b>구별되지 않아야</b> 한다.
     *
     * <p>둘을 나눠 알려주면 밖에서 이메일만 바꿔 넣어보며 가입 여부를 알아낼 수 있다.
     * 코드도 문구도 같아야 하므로 둘 다 비교한다.
     */
    @Test
    void 없는_이메일과_틀린_비밀번호는_같은_실패다() {
        when(wholesalerRepository.findByEmail("nobody@ondo.test")).thenReturn(Optional.empty());

        Wholesaler 도매처 = 도매처(1L, "owner@ondo.test", ApprovalStatus.APPROVED);
        when(wholesalerRepository.findByEmail("owner@ondo.test")).thenReturn(Optional.of(도매처));
        when(passwordEncoder.matches("틀린비번", 해시)).thenReturn(false);

        ApiException 없는_이메일 = catchThrowableOfType(ApiException.class,
                () -> service.authenticate(new LoginRequest("nobody@ondo.test", 비밀번호)));
        ApiException 틀린_비밀번호 = catchThrowableOfType(ApiException.class,
                () -> service.authenticate(new LoginRequest("owner@ondo.test", "틀린비번")));

        assertThat(없는_이메일).isNotNull();
        assertThat(틀린_비밀번호).isNotNull();
        assertThat(없는_이메일.errorCode())
                .as("어느 쪽이 틀렸는지 코드로 갈리면 안 된다")
                .isEqualTo(ErrorCode.LOGIN_FAILED)
                .isEqualTo(틀린_비밀번호.errorCode());
        assertThat(없는_이메일.getMessage())
                .as("문구로도 갈리면 안 된다")
                .isEqualTo(틀린_비밀번호.getMessage());
    }

    /**
     * 없는 이메일이어도 비밀번호 대조를 건너뛰지 않는다.
     *
     * <p>계정을 못 찾자마자 던지면 응답이 눈에 띄게 빨라진다. BCrypt 는 무차별 대입을
     * 막으려고 일부러 느리게 만든 계산이라, 그걸 건너뛴 경로는 수십 배 빠르다.
     * 그러면 응답 <b>내용</b>이 같아도 <b>시간</b>으로 가입 여부가 새어나간다.
     */
    @Test
    void 없는_이메일이어도_비밀번호_대조를_건너뛰지_않는다() {
        when(wholesalerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("nobody@ondo.test", 비밀번호)))
                .isInstanceOf(ApiException.class);

        verify(passwordEncoder)
                .matches(eq(비밀번호), anyString());
    }

    @Test
    void 대문자로_입력해도_소문자로_조회한다() {
        when(wholesalerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("Owner@Ondo.TEST", 비밀번호)))
                .isInstanceOf(ApiException.class);

        verify(wholesalerRepository)
                .findByEmail("owner@ondo.test");
    }

    /**
     * 심사 중이거나 거절된 계정도 <b>로그인은 된다.</b>
     *
     * <p>심사 현황 화면을 봐야 하기 때문이다. 세션은 "누구냐"고 승인은 "무엇을 할 수
     * 있느냐"라 서로 다른 질문이다. 승인 여부로 막는 건 {@code ApprovedAuthorizationManager}
     * 가 보호 API 앞에서 한다.
     */
    @ParameterizedTest
    @EnumSource(ApprovalStatus.class)
    void 승인_상태와_무관하게_인증에_성공한다(ApprovalStatus 상태) {
        Wholesaler 도매처 = 도매처(7L, "owner@ondo.test", 상태);
        when(wholesalerRepository.findByEmail("owner@ondo.test")).thenReturn(Optional.of(도매처));
        when(passwordEncoder.matches(비밀번호, 해시)).thenReturn(true);

        WholesalePrincipal 결과 = service.authenticate(new LoginRequest("owner@ondo.test", 비밀번호));

        assertThat(결과).isEqualTo(new WholesalePrincipal(7L, "owner@ondo.test", 상태));
    }

    private Wholesaler 도매처(Long id, String email, ApprovalStatus 상태) {
        Wholesaler 도매처 = mock(Wholesaler.class);
        when(도매처.getId()).thenReturn(id);
        when(도매처.getEmail()).thenReturn(email);
        when(도매처.getPasswordHash()).thenReturn(해시);
        when(도매처.getApprovalStatus()).thenReturn(상태);
        return 도매처;
    }
}
