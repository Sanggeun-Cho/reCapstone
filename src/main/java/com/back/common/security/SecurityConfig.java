package com.back.common.security;

import com.back.auth.AuthProperties;
import com.back.auth.web.AbsoluteSessionTimeoutFilter;
import com.back.common.logging.RequestIdFilter;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 기본 거부 보안 골격. 공개 경로만 명시해 열고, 나머지는 인증을 요구한다.
 * 401·403도 전역 예외 처리기를 거쳐 {@code ProblemDetail}로 응답한다.
 * <p>
 * 인증은 서버 세션(Spring Session + Redis)으로 한다. 세션 쿠키를 쓰므로 CSRF 방어를 켠다 (SPA용 쿠키 토큰).
 * 신원만 세션에 두고, 권한은 요청마다 상태로 계산한다 (정책 4.1).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String SESSION_COOKIE = "SESSION";

    static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/login/mfa",
            "/api/v1/auth/email-verification",
            "/api/v1/auth/email-verification/resend",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/confirm"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            Clock clock,
            AuthProperties authProperties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 인증 실패 시 요청을 세션에 저장하지 않는다. 익명 요청마다 세션이 생기는 것을 막는다.
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(clock, authProperties.sessionAbsoluteTimeout()),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e)));
        return http.build();
    }

    /**
     * 세션 쿠키. 스크립트가 읽을 수 없고(HttpOnly), HTTPS로만 보내며(Secure), 다른 사이트에서 시작한 요청에는 붙지 않는다(SameSite=Lax).
     * 배포 형태(내장 서버·WAR·테스트)와 관계없이 같은 속성을 쓰도록 직접 등록한다.
     */
    @Bean
    public CookieSerializer sessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 기본은 BCrypt. 저장값에 알고리즘 접두어({@code {bcrypt}})가 붙어 나중에 알고리즘을 바꿀 수 있다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addExposedHeader(RequestIdFilter.HEADER);
        // 세션 쿠키를 주고받는다. 허용 출처는 목록으로만 지정하고 와일드카드는 쓰지 않는다.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
