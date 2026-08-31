package com.ondo.wholesale.common.response;

import com.ondo.wholesale.common.error.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 모든 @ResponseBody 성공 응답을 {@link ApiResponse} 봉투로 자동 래핑한다.
 *
 * <p>단건도 예외 없이 {@code { "data": ... }} 로 감싼다(요구 3). 아래는 감싸지 않는다:
 * <ul>
 *   <li>{@code null}(204 No Content) — 본문 없음</li>
 *   <li>이미 {@link ApiResponse} — 이중 봉투 금지</li>
 *   <li>{@link ProblemDetail} 및 에러 응답 — 에러는 data 봉투를 쓰지 않는다(요구 4)</li>
 *   <li>{@code byte[]}·{@link Resource} — 파일/바이너리</li>
 *   <li>{@code String} — {@link StringHttpMessageConverter} 선택 시 ApiResponse 를 String 으로
 *       변환하지 못해 ClassCastException 이 나므로 {@link #supports} 에서 제외한다</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return !StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null
                || body instanceof ApiResponse<?>
                || body instanceof ErrorResponse
                || body instanceof ProblemDetail
                || body instanceof byte[]
                || body instanceof Resource) {
            return body;
        }
        return ApiResponse.of(body);
    }
}
