package com.back.common.logging;

import java.util.regex.Pattern;

/**
 * 로그 메시지에서 비밀번호·토큰 값을 가린다.
 */
public final class LogMasker {

    static final String MASK = "****";

    // password, newPassword, accessToken, refresh_token, secret 등
    private static final String SENSITIVE_KEY = "[\\w-]*(?:password|token|secret)[\\w-]*";

    // JSON: "key": "value". 값은 이스케이프된 따옴표를 포함해 닫는 따옴표까지 가린다.
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(?i)(\"" + SENSITIVE_KEY + "\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");
    // key=value, key: value. 값은 다음 구분자(공백, &, 쉼표, 따옴표, 중괄호) 앞까지 가린다.
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(?<![\\w\"-])(" + SENSITIVE_KEY + "\\s*[:=]\\s*)(?![\\s\"])[^\\s&,\"}]+");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[\\w\\-.~+/]+=*");

    private LogMasker() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = JSON_FIELD.matcher(message).replaceAll("$1\"" + MASK + "\"");
        masked = KEY_VALUE.matcher(masked).replaceAll("$1" + MASK);
        return BEARER.matcher(masked).replaceAll("$1" + MASK);
    }
}
