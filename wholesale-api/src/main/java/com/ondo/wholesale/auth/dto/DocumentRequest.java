package com.ondo.wholesale.auth.dto;

import com.ondo.wholesale.wholesaler.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 증빙 서류 한 건.
 *
 * <p>{@code fileKey} 는 <b>저장소 키</b>다 — {@code uploads/2026/09/ab12cd34.jpg}.
 * 절대 URL 이 아니다. 스킴과 호스트를 받지 않으므로 외부 주소를 끼워 넣을 수 없다.
 *
 * <p>지금 보는 건 키의 <b>형식</b>뿐이다. "이 사람이 올린 파일이 맞는지" 까지는 못 본다.
 * TODO(MUL-45): presigned 발급 API 가 서버에서 키를 채번해 신청에 묶으면
 * 남의 키를 가리키는 경우까지 막힌다.
 */
public record DocumentRequest(

        @NotNull(message = "서류 종류를 지정해주세요.")
        DocumentType type,

        @NotBlank(message = "서류 파일을 올려주세요.")
        @Size(max = 500, message = "파일 경로가 너무 깁니다.")
        // uploads/ 로 시작하는 상대 경로만. 스킴(https://) · 상위 경로(..) · 공백을 모두 막는다
        @Pattern(regexp = "^uploads/[A-Za-z0-9][A-Za-z0-9/_.-]*$",
                message = "올바른 파일 경로가 아닙니다.")
        String fileKey) {
}
