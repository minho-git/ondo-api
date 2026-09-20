package com.ondo.retail.order;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 접수 대기함 설정을 켠다 (MUL-139). */
@Configuration
@EnableConfigurationProperties(OrderDispatchProperties.class)
public class OrderDispatchConfig {
}
