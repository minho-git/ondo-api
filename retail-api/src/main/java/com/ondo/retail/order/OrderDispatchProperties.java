package com.ondo.retail.order;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 접수 대기함 설정 (MUL-139).
 *
 * @param maxWait 소매처를 기다리게 할 수 있는 시간. 실제 기한은 이 값과 영업일 경계 중
 *                이른 쪽이다({@link DispatchDeadline}).
 *                <p>길게 잡을수록 그동안 다른 도매에서 살 기회를 뺏는 셈이라, 결국 못
 *                넣으면 손해가 커진다. 짧게 잡으면 재시도할 틈이 없어 기능이 무의미해진다.
 */
@ConfigurationProperties(prefix = "ondo.order.dispatch")
public record OrderDispatchProperties(Duration maxWait) {

    public OrderDispatchProperties {
        maxWait = maxWait != null ? maxWait : Duration.ofMinutes(30);
    }
}
