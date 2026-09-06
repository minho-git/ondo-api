package com.ondo.wholesale.common.error;

import com.ondo.wholesale.common.trace.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 전역 예외 핸들러. 예외를 공통 {@link ErrorResponse}로 매핑한다(요구 4·5).
 *
 * <p>401/403은 필터 체인(디스패처 진입 전)에서 발생하므로 여기서 못 잡는다.
 * 인증/인가 실패 응답은 SecurityConfig의 EntryPoint/AccessDeniedHandler가 직접 처리한다(커밋 3·4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.errorCode(), ex.getMessage(), ex.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<ErrorResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    /** 필수 요청 헤더 누락(예: Idempotency-Key) — 클라이언트 요청 문제라 400 이다. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(ex.getHeaderName(), "필수 헤더가 없습니다."));
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    /** 쿼리 파라미터 타입 불일치(예: 날짜 형식 오류) — 클라이언트 입력 문제라 400 이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(ex.getName(), "값의 형식이 올바르지 않습니다."));
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
                                                List<ErrorResponse.FieldError> errors) {
        ErrorResponse body = ErrorResponse.of(code, message, errors, MDC.get(TraceIdFilter.TRACE_ID));
        return ResponseEntity.status(code.status()).body(body);
    }

    /** {@code request.name} 같은 경로에서 마지막 노드(필드명)만 뽑는다. */
    private String lastNode(String propertyPath) {
        int i = propertyPath.lastIndexOf('.');
        return i >= 0 ? propertyPath.substring(i + 1) : propertyPath;
    }
}
