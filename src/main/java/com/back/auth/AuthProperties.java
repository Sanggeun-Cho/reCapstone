package com.back.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원·인증 설정 (정책 제7장). 값을 비워 두면 정책에 정한 기본값을 쓴다.
 *
 * @param frontendUrl       메일 링크가 가리키는 프론트엔드 주소
 * @param mailFrom          발신 주소
 * @param totpEncryptionKey TOTP 비밀키 암호화 키 (Base64로 인코딩한 32바이트)
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String frontendUrl,
        String mailFrom,
        String totpEncryptionKey,
        Duration emailVerificationTtl,
        Duration passwordResetTtl,
        Duration mfaPendingTtl,
        Duration sessionAbsoluteTimeout,
        int maxLoginFailures,
        Duration lockDuration) {

    public AuthProperties {
        emailVerificationTtl = emailVerificationTtl == null ? Duration.ofHours(24) : emailVerificationTtl;
        passwordResetTtl = passwordResetTtl == null ? Duration.ofMinutes(30) : passwordResetTtl;
        mfaPendingTtl = mfaPendingTtl == null ? Duration.ofMinutes(5) : mfaPendingTtl;
        sessionAbsoluteTimeout = sessionAbsoluteTimeout == null ? Duration.ofDays(30) : sessionAbsoluteTimeout;
        maxLoginFailures = maxLoginFailures <= 0 ? 5 : maxLoginFailures;
        lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
    }
}
