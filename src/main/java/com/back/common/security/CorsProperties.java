package com.back.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필별 CORS 허용 출처. 환경변수 {@code CORS_ALLOWED_ORIGINS}(쉼표 구분)로 지정한다.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
