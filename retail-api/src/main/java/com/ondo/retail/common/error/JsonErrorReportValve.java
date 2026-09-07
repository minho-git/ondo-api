package com.ondo.retail.common.error;

import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;

/**
 * 톰캣이 스스로 내보내는 응답을 우리 JSON 규약으로 바꾼다 (MUL-106).
 *
 * <p><b>예외 처리를 바꾸는 게 아니다.</b> 거절하는 판단과 상태 코드는 그대로 두고,
 * 그 결과를 <b>어떤 모양으로 쓰느냐</b>만 바꾼다.
 *
 * <p>주소가 깨지면({@code %} 하나만 있거나 {@code |} 가 섞이면) 톰캣이 요청 줄을 읽는
 * 단계에서 거절한다. 그 요청은 서블릿까지 못 가고 스프링 요청이 된 적이 없어서
 * {@code GlobalExceptionHandler} 가 안 불린다. 핸들러를 아무리 늘려도 안 잡힌다.
 *
 * <p>그래서 우리 API 가 두 가지 모양으로 대답하고 있었다.
 *
 * <pre>
 *   /api/retail/nothing    401  {"code":"UNAUTHORIZED", ...}   우리 규약
 *   /api/retail/listings%  400  &lt;!doctype html&gt;...             톰캣 기본 페이지
 * </pre>
 *
 * <p>프론트는 그 둘을 구분할 방법이 없다. {@code res.json()} 이 HTML 을 만나면 터진다.
 *
 * <p>덤으로 톰캣 버전 노출도 막힌다 — 기본 페이지엔 {@code Apache Tomcat/11.x} 가 찍힌다.
 */
public class JsonErrorReportValve extends ErrorReportValve {

    public JsonErrorReportValve() {
        // 기본 페이지의 서버 정보·스택 요약을 끈다. 우리가 본문을 쓰므로 안 쓰이지만,
        // 이 밸브가 다른 경로로 기본 동작을 타더라도 정보가 새지 않게 둔다
        setShowServerInfo(false);
        setShowReport(false);
    }

    /**
     * 본문이 아직 안 쓰인 에러 응답에만 불린다.
     *
     * <p>스프링이 이미 우리 JSON 을 쓴 응답은 여기 안 온다 — 톰캣이 "본문이 있다" 를
     * 보고 건너뛴다. 그래서 정상 경로의 에러 응답을 덮어쓸 걱정이 없다.
     */
    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        if (response.isCommitted() || response.getStatus() < 400 || response.getContentWritten() > 0) {
            return;
        }

        ErrorCode code = ErrorCode.of(response.getStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (Writer writer = response.getReporter()) {
            if (writer == null) {
                // 이미 닫힌 응답이다. 여기서 뭘 더 할 수 있는 게 없다
                return;
            }
            writer.write(body(code));
        } catch (IOException ignored) {
            // 연결이 끊긴 경우다. 어차피 받을 사람이 없다
        }
    }

    /**
     * {@link ErrorResponse} 를 직접 안 쓰고 문자열로 만든다.
     *
     * <p>이 밸브는 스프링 컨텍스트 밖에서 톰캣이 만들고 부른다 — Jackson 을 주입받을
     * 자리가 없다. 필드가 넷뿐이고 값도 우리가 아는 것뿐이라 손으로 쓰는 게 낫다.
     *
     * <p>모양은 {@code ErrorResponse} 와 같아야 한다. 거기가 바뀌면 여기도 바꾼다.
     */
    private static String body(ErrorCode code) {
        return """
                {"code":"%s","message":"%s","traceId":null,"errors":[]}"""
                .formatted(code.name(), code.message());
    }
}
