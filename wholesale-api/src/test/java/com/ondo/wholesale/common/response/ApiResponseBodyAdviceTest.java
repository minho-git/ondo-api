package com.ondo.wholesale.common.response;

import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성공 응답 봉투 자동 래핑(요구 3 / AC3) 단위 검증.
 */
class ApiResponseBodyAdviceTest {

    private final ApiResponseBodyAdvice advice = new ApiResponseBodyAdvice();

    private record SampleDto(String name) {}

    private Object write(Object body) {
        return advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);
    }

    @Test
    void DTO는_ApiResponse_봉투로_감싼다() {
        Object result = write(new SampleDto("x"));

        assertThat(result).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) result).data()).isEqualTo(new SampleDto("x"));
    }

    @Test
    void 이미_ApiResponse면_이중으로_감싸지_않는다() {
        ApiResponse<String> already = ApiResponse.of("x");

        assertThat(write(already)).isSameAs(already);
    }

    @Test
    void ErrorResponse는_감싸지_않는다() {
        ErrorResponse error = ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, "없음", null, "trace-1");

        assertThat(write(error)).isSameAs(error);
    }

    @Test
    void ProblemDetail은_감싸지_않는다() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        assertThat(write(pd)).isSameAs(pd);
    }

    @Test
    void null이면_그대로_null_이다() {
        assertThat(write(null)).isNull();
    }

    @Test
    void byte배열은_감싸지_않는다() {
        byte[] bytes = {1, 2, 3};

        assertThat(write(bytes)).isSameAs(bytes);
    }

    @Test
    void Resource는_감싸지_않는다() {
        ByteArrayResource resource = new ByteArrayResource(new byte[] {1});

        assertThat(write(resource)).isSameAs(resource);
    }

    @Test
    void supports_String컨버터는_제외한다() {
        assertThat(advice.supports(null, StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void supports_String이_아닌_컨버터는_포함한다() {
        assertThat(advice.supports(null, ByteArrayHttpMessageConverter.class)).isTrue();
    }
}
