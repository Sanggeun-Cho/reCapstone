package com.back.auth.support;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * 복구 코드 생성과 정규화. 형식은 {@code xxxxx-xxxxx}이며, 헷갈리는 문자(0·o·1·l·i)는 쓰지 않는다.
 * <p>
 * 코드당 약 49비트의 무작위성이 있어 BCrypt 대신 SHA-256으로 해시한다 (로그인할 때 10개를 대조해야 하기 때문).
 */
public final class RecoveryCodes {

    public static final int COUNT = 10;

    private static final char[] ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final int HALF = 5;

    private RecoveryCodes() {
    }

    public static List<String> generate() {
        return IntStream.range(0, COUNT).mapToObj(i -> randomChunk() + "-" + randomChunk()).toList();
    }

    public static String hash(String code) {
        return Hashing.sha256Hex(normalize(code));
    }

    /** 대소문자, 하이픈, 공백 차이는 무시한다. */
    static String normalize(String code) {
        return code.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    private static String randomChunk() {
        StringBuilder chunk = new StringBuilder(HALF);
        for (int i = 0; i < HALF; i++) {
            chunk.append(ALPHABET[Hashing.randomInt(ALPHABET.length)]);
        }
        return chunk.toString();
    }
}
