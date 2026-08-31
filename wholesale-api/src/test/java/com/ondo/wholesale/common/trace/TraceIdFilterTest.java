package com.ondo.wholesale.common.trace;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청당 traceId 발급/전파(요구 4 traceId) 단위 검증.
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void 헤더가_없으면_UUID를_생성하고_응답헤더에_반영하며_처리후_MDC를_정리한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];

        filter.doFilter(request, response, (req, res) -> captured[0] = MDC.get(TraceIdFilter.TRACE_ID));

        assertThat(captured[0]).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(captured[0]);
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    void 수신_X_Request_Id_헤더가_있으면_그_값을_채택한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "given-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];

        filter.doFilter(request, response, (req, res) -> captured[0] = MDC.get(TraceIdFilter.TRACE_ID));

        assertThat(captured[0]).isEqualTo("given-trace-123");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("given-trace-123");
    }
}
