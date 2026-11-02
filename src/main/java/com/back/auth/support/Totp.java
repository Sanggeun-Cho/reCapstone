package com.back.auth.support;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30초, 6자리). 인증 앱(Google Authenticator 등)의 기본값과 같다.
 * <p>
 * 알고리즘이 짧고 표준 테스트 벡터로 검증할 수 있어 라이브러리 없이 직접 구현했다.
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    static final int SECRET_BYTES = 20;

    /** 시계 오차를 고려해 앞뒤로 허용하는 구간 수 */
    static final int WINDOW = 1;

    private static final String ISSUER = "인계점";
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {
    }

    public static byte[] newSecret() {
        return Hashing.randomBytes(SECRET_BYTES);
    }

    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PERIOD_SECONDS);
    }

    public static String codeAt(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 코드를 검증하고, 맞으면 그 코드의 시간 구간을 돌려준다.
     *
     * @param lastUsedStep 마지막으로 쓴 구간. 이 구간 이하의 코드는 재사용으로 보고 거부한다 (없으면 null)
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        long current = stepAt(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (Hashing.constantTimeEquals(codeAt(secret, step), code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return out.toString();
    }

    /** 인증 앱에 등록할 URI. 프론트엔드가 QR 코드로 보여 준다. */
    public static String otpauthUri(String accountName, byte[] secret) {
        String issuer = encode(ISSUER);
        return "otpauth://totp/" + issuer + ":" + encode(accountName)
                + "?secret=" + base32(secret)
                + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
