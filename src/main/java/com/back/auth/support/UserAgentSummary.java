package com.back.auth.support;

/**
 * User-Agent를 "브라우저 / 운영체제" 요약으로 줄인다. 원문과 IP는 저장하지 않는다 (정책 7.4).
 */
public final class UserAgentSummary {

    static final String UNKNOWN = "기타";

    private UserAgentSummary() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN + " / " + UNKNOWN;
        }
        return browser(userAgent) + " / " + os(userAgent);
    }

    // 다른 브라우저 이름을 함께 담는 문자열이 많아 구체적인 것부터 확인한다 (Edge는 Chrome을, Chrome은 Safari를 포함).
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return UNKNOWN;
    }

    private static String os(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }
}
