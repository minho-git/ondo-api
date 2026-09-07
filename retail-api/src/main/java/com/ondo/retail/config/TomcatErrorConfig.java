package com.ondo.retail.config;

import com.ondo.retail.common.error.JsonErrorReportValve;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 톰캣이 스스로 내보내는 에러 응답을 우리 JSON 규약으로 바꾼다 (MUL-106).
 *
 * <p>주소가 깨지면 톰캣이 요청 줄을 읽는 단계에서 거절한다. 그 요청은 스프링 요청이
 * 된 적이 없어서 {@code GlobalExceptionHandler} 가 안 불리고, 톰캣이 자기 HTML 페이지를
 * 대신 그린다. 그 그리는 부품({@code ErrorReportValve})을 우리 것으로 갈아끼운다.
 *
 * <p><b>클래스 이름을 문자열로 준다.</b> 톰캣이 그 이름으로 직접 만들어 쓴다 —
 * 우리가 만든 객체를 넘기는 게 아니라서 스프링 빈을 주입할 수 없다. 밸브가 Jackson 을
 * 안 쓰고 문자열로 본문을 쓰는 이유도 이것이다.
 */
@Configuration
public class TomcatErrorConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonErrorReport() {
        return factory -> factory.addContextCustomizers(context ->
                ((StandardHost) context.getParent())
                        .setErrorReportValveClass(JsonErrorReportValve.class.getName()));
    }
}
