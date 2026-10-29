package com.back.common.logging;

import java.util.regex.Pattern;

/**
 * 로그 메시지에서 비밀번호·토큰 값을 가린다.
 */
public final class LogMasker {

    static final String MASK = "****";

    // password, newPassword, accessToken, refresh_token, secret 등의 키 뒤에 오는 값
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)([\\w-]*(?:password|token|secret)[\\w-]*\"?\\s*[:=]\\s*\"?)[^\"\\s,&}]+");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[\\w\\-.~+/]+=*");

    private LogMasker() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = SENSITIVE_FIELD.matcher(message).replaceAll("$1" + MASK);
        return BEARER.matcher(masked).replaceAll("$1" + MASK);
    }
}
