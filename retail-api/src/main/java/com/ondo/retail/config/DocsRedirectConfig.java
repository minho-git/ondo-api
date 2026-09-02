package com.ondo.retail.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /docs 로 들어와도 문서가 열리게 한다.
 *
 * <p>정적 파일은 /docs/index.html 에 있는데, 스프링은 디렉터리 색인을 만들어주지 않아서
 * /docs 만 치면 404 다. 프론트한테 알려줄 주소를 짧게 두려고 넘겨준다.
 */
@Configuration
public class DocsRedirectConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/docs", "/docs/index.html");
        registry.addRedirectViewController("/docs/", "/docs/index.html");
    }
}
