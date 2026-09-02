package com.ondo.retail.auth.dto;

import com.ondo.retail.retailer.domain.TermsType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 가입 신청의 payload 파트. 사업자등록증 파일은 별도 파트로 온다.
 *
 * <p>로그인과 달리 이메일 형식을 검사한다. 가입은 값을 저장하는 자리라
 * 틀린 형식을 알려주는 게 맞고, 계정 존재 여부가 새지도 않는다.
 *
 * @param email       로그인 계정. 이미 가입돼 있으면 409 다
 * @param password    8자 이상 64자까지
 * @param shopName    상호. 화면에 보이는 이름이다
 * @param ownerName   대표자명. 사업자등록증과 같아야 승인된다
 * @param mobile      하이픈 없이 숫자만 10~11자리
 * @param bizRegNo    사업자등록번호. 하이픈 없이 숫자 10자리
 * @param agreedTerms 동의한 약관. 필수 약관이 빠지면 400 이다
 */
public record SignUpRequest(

        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "이메일 형식이 아니에요")
        @Size(max = 100, message = "이메일이 너무 길어요")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 해요")
        String password,

        @NotBlank(message = "상호를 입력해주세요")
        @Size(max = 50, message = "상호는 50자까지 쓸 수 있어요")
        String shopName,

        @NotBlank(message = "대표자명을 입력해주세요")
        String ownerName,

        @NotBlank(message = "휴대전화번호를 입력해주세요")
        @Pattern(regexp = "\\d{10,11}", message = "숫자만 10~11자리로 입력해주세요")
        String mobile,

        @NotBlank(message = "사업자등록번호를 입력해주세요")
        @Pattern(regexp = "\\d{10}", message = "숫자 10자리로 입력해주세요")
        String bizRegNo,

        @NotEmpty(message = "약관에 동의해주세요")
        List<TermsType> agreedTerms) {
}
