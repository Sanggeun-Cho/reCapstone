package com.back.auth.support;

import com.back.auth.AuthProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TOTP 비밀키 같은 작은 비밀값을 AES-256-GCM으로 암호화한다. 저장 형식은 Base64(IV 12바이트 + 암호문·태그)다.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    @Autowired
    public SecretCipher(AuthProperties properties) {
        this(decodeKey(properties.totpEncryptionKey()));
    }

    SecretCipher(byte[] key) {
        if (key.length != 32) {
            throw new IllegalStateException("app.auth.totp-encryption-key must be 32 bytes (Base64)");
        }
        this.key = new SecretKeySpec(key, "AES");
    }

    public String encrypt(byte[] plaintext) {
        try {
            byte[] iv = Hashing.randomBytes(IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public byte[] decrypt(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }

    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("app.auth.totp-encryption-key (TOTP_ENCRYPTION_KEY) is required");
        }
        return Base64.getDecoder().decode(base64);
    }
}
