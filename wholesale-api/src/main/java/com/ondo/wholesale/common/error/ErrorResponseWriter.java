package com.ondo.wholesale.common.error;

import com.ondo.wholesale.common.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * 필터 체인(EntryPoint/AccessDeniedHandler)에서 공통 에러 포맷 JSON을 직접 써주는 공용 도구.
 *
 * <p>401/403은 디스패처 진입 전에 발생해 @RestControllerAdvice가 못 잡으므로, 여기서 직접 직렬화한다.
 * errors는 필터단에서는 항상 빈 배열이며, traceId는 {@link TraceIdFilter}가 MDC에 넣은 값을 읽는다.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(code, code.defaultMessage(), List.of(), MDC.get(TraceIdFilter.TRACE_ID));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
