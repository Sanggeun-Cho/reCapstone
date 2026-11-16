package com.back.file.support;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 파일·폴더 이름 규칙 (정책 8.3). 이름은 NFC로 정규화해 저장하고, 비교는 NFC + 소문자 키로 한다.
 */
public final class ItemNames {

    public static final int MAX_LENGTH = 255;

    /** Windows에서 쓸 수 없는 이름. 확장자가 붙어도 금지한다 (정책 8.3 제1항) */
    private static final Pattern RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");

    private ItemNames() {
    }

    /** NFC로 정규화한다. macOS가 보내는 NFD 한글 이름을 같은 이름으로 다루기 위해서다. */
    public static String normalize(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    /** 이름 비교용 키: NFC + 소문자 (정책 8.3 제2항). */
    public static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT);
    }

    /** 규칙을 지키면 정규화한 이름을, 어기면 {@code INVALID_ITEM_NAME}을 던진다. */
    public static String validate(String name) {
        String normalized = normalize(name);
        if (!isValid(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ITEM_NAME);
        }
        return normalized;
    }

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = normalize(name);
        int length = normalized.codePointCount(0, normalized.length());
        if (length > MAX_LENGTH) {
            return false;
        }
        boolean forbiddenChar = normalized.codePoints()
                .anyMatch(c -> c == '/' || c == '\\' || Character.isISOControl(c));
        if (forbiddenChar) {
            return false;
        }
        char last = normalized.charAt(normalized.length() - 1);
        if (last == '.' || last == ' ') {
            return false;
        }
        int dot = normalized.indexOf('.');
        String base = dot < 0 ? normalized : normalized.substring(0, dot);
        return !RESERVED.matcher(base).matches();
    }

    /**
     * {@code taken}에 없는 이름을 고른다. 겹치면 {@code 이름 (1).확장자}처럼 번호를 붙인다 (정책 8.4 제1항).
     * 번호를 붙여 255자를 넘으면 확장자 앞부분을 줄인다.
     *
     * @param taken 이미 쓰는 이름의 키({@link #key})인지 판단한다
     */
    public static String available(String name, Predicate<String> taken) {
        if (!taken.test(key(name))) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        // ".env"처럼 점으로 시작하는 이름은 확장자가 없는 것으로 본다
        String base = dot <= 0 ? name : name.substring(0, dot);
        String extension = dot <= 0 ? "" : name.substring(dot);
        for (int n = 1; ; n++) {
            String suffix = " (" + n + ")";
            String candidate = fit(base, suffix + extension) + suffix + extension;
            if (!taken.test(key(candidate))) {
                return candidate;
            }
        }
    }

    public static String available(String name, Set<String> takenKeys) {
        return available(name, takenKeys::contains);
    }

    private static String fit(String base, String tail) {
        int room = MAX_LENGTH - tail.codePointCount(0, tail.length());
        if (base.codePointCount(0, base.length()) <= room) {
            return base;
        }
        return base.substring(0, base.offsetByCodePoints(0, Math.max(room, 1)));
    }
}
