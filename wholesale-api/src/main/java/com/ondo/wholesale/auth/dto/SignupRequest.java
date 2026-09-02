package com.ondo.wholesale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 가입 신청. 계정 · 사업자 정보 · 증빙 서류 · 동의를 한 번에 받는다. 중간 저장은 없다.
 *
 * <p>여기서 거르는 건 <b>형식</b>뿐이고, 어기면 전부 {@code VALIDATION_FAILED} 다.
 * 비밀번호 정책 · 필수 동의 · 필수 서류 같은 <b>정책</b>은 서비스가 보고 전용 코드를 낸다.
 * 형식 위반은 요청 계약의 문제라 하나로 묶어도 되지만, 정책 위반은 화면이 다르게
 * 처리해야 해서 코드를 나눈다.
 *
 * <p>그래서 {@code password} 에는 {@code @NotBlank} 만 있다. 길이와 문자 조합을 여기서
 * {@code @Pattern} 으로 보면 {@code VALIDATION_FAILED} 로 뭉개져
 * {@code PASSWORD_POLICY_VIOLATED} 를 낼 수 없다.
 *
 * <p>화면의 "주요 취급 카테고리" 는 받지 않는다. 매핑이 확정되지 않아 저장할 자리가
 * 없기 때문이다. 프론트가 보내도 400 이 나지는 않는다 — 스프링 기본값이 모르는 필드를
 * 무시한다({@code FAIL_ON_UNKNOWN_PROPERTIES=false}).
 */
public record SignupRequest(

        @Schema(example = "owner@dodo.example")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일이 너무 깁니다.")
        String email,

        // 길이·문자 조합은 서비스가 본다(PASSWORD_POLICY_VIOLATED)
        @Schema(example = "Ondo!2345")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        @Schema(example = "01012345678")
        @Pattern(regexp = "\\d{10,11}", message = "휴대전화번호는 숫자 10~11자리입니다.")
        String phone,

        @Schema(example = "1234567890")
        @NotBlank(message = "사업자등록번호를 입력해주세요.")
        @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 숫자 10자리입니다.")
        String bizRegNo,

        @Schema(example = "도도도매")
        @NotBlank(message = "상호를 입력해주세요.")
        @Size(max = 50, message = "상호는 50자까지 쓸 수 있습니다.")
        String bizName,

        @Schema(example = "김도매")
        @NotBlank(message = "대표자명을 입력해주세요.")
        @Size(max = 50, message = "대표자명은 50자까지 쓸 수 있습니다.")
        String bizOwnerName,

        @Schema(example = "025551234")
        @Pattern(regexp = "\\d{9,11}", message = "매장 전화번호는 숫자 9~11자리입니다.")
        String storePhone,

        @Schema(example = "디오트")
        @Size(max = 50, message = "건물명은 50자까지 쓸 수 있습니다.")
        String storeBuilding,

        @Schema(example = "B1 123호")
        @Size(max = 50, message = "호수는 50자까지 쓸 수 있습니다.")
        String storeUnit,

        @Schema(example = "여성의류")
        @Size(max = 50, message = "업종은 50자까지 쓸 수 있습니다.")
        String bizCategory,

        // 리스트가 비었는지는 서비스가 본다(REQUIRED_DOCUMENT_MISSING · REQUIRED_CONSENT_MISSING).
        // @Valid 는 들어온 항목 하나하나의 형식을 마저 본다는 뜻이다
        @Valid List<DocumentRequest> documents,

        @Valid List<ConsentRequest> consents) {

    /**
     * 리스트를 절대 null 로 두지 않는다. 서비스가 매번 null 을 확인하지 않아도 되고,
     * 비어 있는 것과 안 보낸 것을 똑같이 "누락" 으로 다룰 수 있다.
     * ({@code ErrorResponse} 가 errors 리스트에 쓰는 방식과 같다)
     */
    public SignupRequest {
        documents = (documents == null) ? List.of() : List.copyOf(documents);
        consents = (consents == null) ? List.of() : List.copyOf(consents);
    }
}
