package com.ondo.retail.auth;

import com.ondo.retail.common.response.ApiResponse;
import com.ondo.retail.retailer.RetailerRepository;
import com.ondo.retail.retailer.domain.Retailer;
import com.ondo.retail.auth.dto.EmailAvailabilityResponse;
import com.ondo.retail.auth.dto.LoginRequest;
import com.ondo.retail.auth.dto.RetailerResponse;
import com.ondo.retail.auth.dto.SignUpRequest;
import com.ondo.retail.auth.dto.SignUpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retail/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    /**
     * 가입 신청. 폼과 사업자등록증을 multipart 로 한 번에 받는다.
     *
     * <p>세션을 주지 않는다. 가입 직후는 늘 PENDING 이라 로그인 화면으로 보내면 된다.
     */
    @PostMapping(value = "/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignUpResponse> signUp(@RequestPart("payload") @Valid SignUpRequest payload,
                                              @RequestPart("bizLicense") MultipartFile bizLicense) {
        return ApiResponse.of(authService.signUp(payload, bizLicense));
    }

    /**
     * 로그인. 성공하면 세션이 생기고 SESSION_RETAIL 쿠키가 붙는다.
     *
     * <p>승인 안 된 계정도 200 이다. 승인 여부는 다른 API 에서 따로 본다.
     */
    @PostMapping("/login")
    public ApiResponse<RetailerResponse> login(@RequestBody @Valid LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        Retailer retailer = authService.login(request);
        authenticate(retailer, httpRequest, httpResponse);
        return ApiResponse.of(RetailerResponse.from(retailer));
    }

    /** 로그아웃. 세션을 지우면 DB 의 spring_session 행도 사라진다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.of(null);
    }

    /** 내 정보. 승인 전에도 열려 있다 — 승인 대기 화면이 이걸로 상태를 본다. */
    @GetMapping("/me")
    public ApiResponse<RetailerResponse> me(Authentication authentication) {
        Long retailerId = Long.valueOf(authentication.getName());
        return ApiResponse.of(authService.me(retailerId));
    }

    /** 가입 화면에서 이메일을 칠 때 부른다. */
    @GetMapping("/email-availability")
    public ApiResponse<EmailAvailabilityResponse> emailAvailability(@RequestParam String email) {
        return ApiResponse.of(new EmailAvailabilityResponse(authService.isEmailAvailable(email)));
    }

    /**
     * 스프링 시큐리티가 알아볼 수 있는 형태로 인증 정보를 심는다.
     *
     * <p>세션에 retailerId 만 넣어두면 시큐리티는 여전히 로그인 안 한 것으로 본다.
     * 이름을 retailerId 로 두는 이유는 spring_session.principal_name 에 그 값이 들어가서,
     * 계정을 정지시킬 때 그 사람 세션만 찾아 지울 수 있기 때문이다.
     */
    private void authenticate(Retailer retailer, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                String.valueOf(retailer.getId()), null, List.of());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        request.getSession(true);                                   // 세션을 만든다
        securityContextRepository.saveContext(context, request, response);
    }
}
