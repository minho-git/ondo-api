package com.ondo.retail.common.error;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 톰캣이 스스로 내는 응답도 우리 JSON 규약을 지키는지 (MUL-106).
 *
 * <p><b>MockMvc 로는 못 본다.</b> 여기서 잡으려는 건 요청이 스프링에 도달하기 <b>전에</b>
 * 끊기는 경우인데, MockMvc 는 톰캣을 안 띄우고 스프링만 흉내 낸다.
 *
 * <p><b>HttpClient 로도 못 보낸다.</b> 자바의 {@code URI} 가 깨진 주소를 먼저 막아서
 * 서버까지 가지도 않는다. 그래서 소켓에 요청 줄을 직접 쓴다 — 브라우저나 프록시가
 * 실제로 보내는 것과 같은 모양이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonErrorReportValveTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("퍼센트 하나만 있는 주소도 JSON 으로 대답한다")
    void 깨진_퍼센트도_JSON이다() throws Exception {
        String response = 날것으로("/api/retail/listings%");

        assertThat(상태(response)).isEqualTo(400);
        assertThat(response).containsIgnoringCase("content-type: application/json");
        // 프론트가 res.json() 해도 안 터진다
        assertThat(본문(response)).contains("\"code\"").contains("\"errors\":[]");
        assertThat(response).doesNotContain("<html").doesNotContain("<!doctype");
    }

    @Test
    @DisplayName("주소에 못 쓰는 글자가 섞여도 JSON 이다")
    void 못쓰는_글자도_JSON이다() throws Exception {
        String response = 날것으로("/api/retail/listings|x");

        assertThat(상태(response)).isEqualTo(400);
        assertThat(response).doesNotContain("<html").doesNotContain("<!doctype");
        assertThat(본문(response)).contains("\"code\"");
    }

    @Test
    @DisplayName("톰캣 버전이 응답에 안 실린다")
    void 톰캣_버전이_안_샌다() throws Exception {
        // 기본 에러 페이지엔 "Apache Tomcat/11.x" 가 찍힌다. 뭘 쓰는지 알려줄 이유가 없다
        assertThat(날것으로("/api/retail/listings%")).doesNotContain("Tomcat");
    }

    @Test
    @DisplayName("스프링이 만든 4xx 는 그대로 나간다 — 밸브가 안 덮는다")
    void 스프링_에러는_안_덮인다() throws Exception {
        // 밸브가 이것까지 덮으면 UNAUTHORIZED 가 VALIDATION_FAILED 로 뭉개진다.
        // 톰캣은 본문이 이미 쓰인 응답을 건너뛰는데, 그게 실제로 그런지 본다
        String response = 날것으로("/api/retail/cart-items");

        assertThat(상태(response)).isEqualTo(401);
        assertThat(본문(response)).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("정상 응답도 그대로다")
    void 정상_응답은_안_덮인다() throws Exception {
        String response = 날것으로("/actuator/health");

        assertThat(상태(response)).isEqualTo(200);
        assertThat(본문(response)).contains("UP");
    }

    // ── 거들기 ─────────────────────────────────────────────────

    /** 소켓에 요청 줄을 그대로 쓴다. 자바 URI 검증을 안 거친다. */
    private String 날것으로(String rawPath) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + rawPath + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            StringBuilder received = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    received.append(line).append('\n');
                }
            }
            return received.toString();
        }
    }

    private static int 상태(String response) {
        // "HTTP/1.1 400 " 에서 가운데 숫자
        return Integer.parseInt(response.split("\n", 2)[0].split(" ")[1].trim());
    }

    /** 헤더와 본문은 빈 줄로 갈린다. */
    private static String 본문(String response) {
        int blank = response.indexOf("\n\n");
        return blank < 0 ? "" : response.substring(blank + 2);
    }
}
