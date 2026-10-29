package com.back.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {

    // 인증 주체의 형태는 S1에서 정한다. 그 전까지는 principal이 사용자 id(Long)일 때만 기록한다.
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            return authentication.getPrincipal() instanceof Long userId ? Optional.of(userId) : Optional.empty();
        };
    }
}
