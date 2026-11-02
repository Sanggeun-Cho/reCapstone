package com.back.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 다루는 코드는 {@link Clock}을 주입받는다. 테스트에서 시간을 움직여 만료·잠금을 검증하기 위함이다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
