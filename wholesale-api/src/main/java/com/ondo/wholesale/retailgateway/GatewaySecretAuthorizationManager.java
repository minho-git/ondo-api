package com.ondo.wholesale.retailgateway;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 소매 접점(/api/retail-gateway/**)의 문지기 (MUL-87).
 *
 * <p>여기를 부르는 건 사람이 아니라 <b>소매 백엔드</b>다. 도매 세션이 없으므로
 * 앞의 2단 게이트({@link ApprovedAuthorizationManager})를 쓸 수 없고, 대신
 * 양쪽이 나눠 가진 시크릿을 헤더로 확인한다 (숙제 5번에서 정한 방식).
 *
 * <p>이 경로는 <b>내부 ALB 로만</b> 들어온다. 공개 ALB 는 이 경로를 라우팅하지 않는다.
 * 그래도 헤더를 보는 건, 같은 VPC 안에서 다른 무언가가 부르는 걸 막기 위해서다 —
 * 네트워크만 믿으면 VPC 안이 뚫렸을 때 도매 데이터가 통째로 열린다.
 *
 * <p><b>시크릿이 비어 있으면 통과시킨다.</b> 로컬 개발용이다. 도매·소매를 각자
 * 띄워 쓰는 팀원들이 시크릿을 맞추지 않아도 돌아가야 한다. 배포에서는 그럴 일이
 * 없다 — {@code application-prod.yml} 이 기본값 없이 환경변수를 요구해서,
 * 값이 없으면 앱이 아예 안 뜬다.
 *
 * <p><b>왜 여기(retailgateway) 에 있나</b> — 도매 {@code security} 패키지는 채빈 영역이고
 * 이건 소매 접점 전용이다. 경계를 지키려고 이 폴더에 둔다. 다만 규칙을 거는
 * {@code SecurityConfig} 는 채빈 파일이라 그건 손댈 수밖에 없다.
 *
 * <p>빈은 {@code SecurityConfig} 가 만든다. {@code @Component} 로 두면
 * {@code @Import(SecurityConfig.class)} 하는 {@code @WebMvcTest} 12개가 이 빈을
 * 따로 적어줘야 해서, 보안 설정을 건드릴 때마다 남의 테스트가 같이 깨진다.
 */
public class GatewaySecretAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /** 소매가 실어 보내는 헤더. 표준 헤더가 아니므로 X- 로 시작한다. */
    public static final String HEADER = "X-Ondo-Gateway-Secret";

    private final byte[] expected;
    private final boolean checkDisabled;

    public GatewaySecretAuthorizationManager(String secret) {
        this.checkDisabled = !StringUtils.hasText(secret);

        // HTTP 헤더 값은 ASCII 만 담는다. 한글이 섞이면 소매가 보낸 헤더가 깨져
        // 여기까지 오지도 못하고 400 이 난다 — 원인을 찾기 어렵다
        if (!checkDisabled && !secret.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            throw new IllegalArgumentException(
                    "ondo.gateway.secret 은 ASCII 로만 적는다. HTTP 헤더로 오는 값이다");
        }
        this.expected = checkDisabled ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        if (checkDisabled) {
            return new AuthorizationDecision(true);
        }
        String presented = context.getRequest().getHeader(HEADER);
        return new AuthorizationDecision(matches(presented));
    }

    /**
     * 길이와 내용을 <b>끝까지</b> 비교한다.
     *
     * <p>{@code String.equals} 는 다른 글자가 나오면 거기서 멈춘다. 앞자리가 맞을수록
     * 느리게 끝나므로, 응답 시간을 재면서 한 글자씩 맞춰 나가는 공격이 가능하다.
     * {@code MessageDigest.isEqual} 은 그런 차이를 안 만든다.
     */
    private boolean matches(String presented) {
        if (!StringUtils.hasText(presented)) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected);
    }
}
