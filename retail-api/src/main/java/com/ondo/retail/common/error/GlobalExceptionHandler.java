package com.ondo.retail.common.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 팀 규약 모양의 실패 응답으로 바꾼다.
 *
 * <p>컨트롤러마다 try-catch 를 두지 않으려고 한곳에 모았다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 우리가 의도적으로 던진 것. 예상된 흐름이라 스택트레이스를 남기지 않는다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.errorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, e.getMessage(), traceId()));
    }

    /** @Valid 가 걸러낸 것. 어느 필드가 왜 걸렸는지 errors[] 에 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(
                        f.getField(),                 // items[2].quantity 처럼 JS 경로로 나온다
                        fieldCode(f.getCode()),       // 걸린 제약 이름. Email → EMAIL
                        f.getDefaultMessage()))
                .toList();

        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, traceId(), errors));
    }

    /**
     * 없는 주소. 스프링이 정상적으로 던지는 것이라 서버 잘못이 아니다.
     * 아래 catch-all 보다 먼저 잡아야 500 으로 새지 않는다.
     */

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, traceId()));
    }

    /** 예상 못 한 것. 원인을 로그에 남기고 사용자에겐 자세히 알려주지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, traceId()));
    }

    /**
     * 스프링이 주는 제약 이름을 우리 코드 표기로 바꾼다. {@code NotBlank → NOT_BLANK}
     *
     * <p>팀 규약이 에러 코드를 「도메인_사유」 대문자 문자열로 정해서 필드 단위도 맞춘다.
     */
    private static String fieldCode(String constraintName) {
        if (constraintName == null) {
            return ErrorCode.VALIDATION_FAILED.name();
        }
        return constraintName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    /**
     * 로그와 응답을 이어주는 값. 사용자가 traceId 를 알려주면 그 요청의 로그를 찾을 수 있다.
     * MDC 를 채우는 필터는 아직 없어서 지금은 비어 있다.
     */
    private String traceId() {
        return MDC.get("traceId");
    }
}
