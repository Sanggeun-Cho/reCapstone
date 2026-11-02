package com.back.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    // RFC 6238 부록 B의 SHA1 비밀키
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "T={0}s -> {1}")
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"
    })
    @DisplayName("TOTP-U1: RFC 6238 테스트 벡터의 끝 6자리와 일치한다")
    void rfcVectors(long epochSecond, String expected) {
        assertThat(Totp.codeAt(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(epochSecond)))).isEqualTo(expected);
    }

    @Test
    @DisplayName("TOTP-U2: 앞뒤 한 구간(30초)의 코드는 허용하고, 두 구간 밖은 거부한다")
    void window() {
        Instant now = Instant.ofEpochSecond(1_000_000_005L);
        long step = Totp.stepAt(now);

        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step), now, null)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 2), now, null)).isEmpty();
    }

    @Test
    @DisplayName("TOTP-U3: 마지막으로 쓴 구간 이하의 코드는 재사용으로 보고 거부한다")
    void replayRejected() {
        Instant now = Instant.ofEpochSecond(1_000_000_005L);
        long step = Totp.stepAt(now);
        String code = Totp.codeAt(RFC_SECRET, step);

        assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1), now, step - 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1), now, step)).hasValue(step + 1);
    }

    @Test
    @DisplayName("6자리 숫자가 아니면 거부한다")
    void malformedCode() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "28708", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "28708a", now, null)).isEmpty();
    }

    @Test
    @DisplayName("Base32 인코딩과 등록 URI")
    void base32AndUri() {
        assertThat(Totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.otpauthUri("bob@example.com", RFC_SECRET))
                .startsWith("otpauth://totp/")
                .contains("bob%40example.com", "secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "digits=6", "period=30");
    }
}
