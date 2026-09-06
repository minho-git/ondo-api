package com.ondo.wholesale.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필드 에러의 {@code data}가 있을 때만 직렬화되는지 못박는다 — 기존 에러 응답의
 * 겉모습(필드 2개)이 안 바뀌어야 클라이언트가 놀라지 않는다.
 */
class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 필드에러_data는_있을때만_직렬화된다() throws Exception {
        String with = objectMapper.writeValueAsString(
                new ErrorResponse.FieldError("items", "예약을 쥔 포장이 있습니다.", Map.of("packingId", 1)));
        String without = objectMapper.writeValueAsString(
                new ErrorResponse.FieldError("items", "예약을 쥔 포장이 있습니다."));

        assertThat(with).contains("\"data\"").contains("\"packingId\":1");
        assertThat(without).doesNotContain("\"data\"");
    }
}
