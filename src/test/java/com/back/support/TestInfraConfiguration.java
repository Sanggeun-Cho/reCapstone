package com.back.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 모든 통합 테스트가 공유하는 테스트 전용 빈: 움직이는 시계, 메일 수신 서버(GreenMail), 탈퇴 차단 스위치.
 * 같은 구성을 공유해야 애플리케이션 컨텍스트와 컨테이너를 한 번만 띄운다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfraConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean(destroyMethod = "stop")
    GreenMail greenMail() {
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
        return greenMail;
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(GreenMail greenMail) {
        return registry -> {
            registry.add("spring.mail.host", () -> "127.0.0.1");
            registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        };
    }

    @Bean
    ToggleableWithdrawalGuard toggleableWithdrawalGuard() {
        return new ToggleableWithdrawalGuard();
    }
}
