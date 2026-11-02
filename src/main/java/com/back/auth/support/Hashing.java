package com.back.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 무작위 값 생성과 SHA-256 해시. 해시는 무작위성이 충분한 값(링크 토큰, 복구 코드, 세션 ID)에만 쓴다.
 * 비밀번호처럼 추측할 수 있는 값은 BCrypt로 해시한다.
 */
public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {
    }

    /** URL에 넣을 수 있는 무작위 토큰 (256비트). */
    public static String randomUrlToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
