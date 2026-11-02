package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.auth.support.SecretCipher;
import com.back.auth.support.Totp;
import com.back.support.AuthApiTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TwoFactorStorageTest extends AuthApiTest {

    @Autowired
    SecretCipher secretCipher;

    @Test
    @DisplayName("AUTH-28: TOTP 비밀키는 암호문으로, 복구 코드는 해시로만 저장된다")
    void secretsNotStoredInPlaintext() throws Exception {
        long id = createActiveUser("bob@example.com");
        TwoFactor twoFactor = enableTwoFactor(loginCookie("bob@example.com"));

        String stored = jdbcTemplate.queryForObject("SELECT totp_secret FROM users WHERE id = ?", String.class, id);
        assertThat(stored).isNotEqualTo(Totp.base32(twoFactor.secret()));
        assertThat(secretCipher.decrypt(stored)).isEqualTo(twoFactor.secret());

        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT code_hash FROM recovery_code WHERE user_id = ?", String.class, id);
        assertThat(hashes).hasSize(10).allMatch(hash -> hash.matches("[0-9a-f]{64}"));
        for (String code : twoFactor.recoveryCodes()) {
            assertThat(hashes).noneMatch(hash -> hash.contains(code) || hash.contains(code.replace("-", "")));
        }
    }
}
