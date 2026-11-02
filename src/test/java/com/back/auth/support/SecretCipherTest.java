package com.back.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Test
    @DisplayName("CIPHER-U1: 복호화하면 원문이 나오고, 같은 평문도 매번 다른 암호문이 된다")
    void roundTrip() {
        byte[] secret = "totp-secret-bytes!!!".getBytes(StandardCharsets.US_ASCII);

        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertThat(cipher.decrypt(first)).isEqualTo(secret);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("CIPHER-U2: 암호문을 1바이트라도 바꾸면 복호화가 실패한다")
    void tamperDetected() {
        byte[] data = Base64.getDecoder().decode(cipher.encrypt(new byte[] {1, 2, 3}));
        data[data.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(data)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 기동하지 않는다")
    void keyLength() {
        assertThatThrownBy(() -> new SecretCipher(new byte[16])).isInstanceOf(IllegalStateException.class);
    }
}
