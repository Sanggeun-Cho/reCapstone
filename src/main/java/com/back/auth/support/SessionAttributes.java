package com.back.auth.support;

/**
 * 인증 기능이 세션에 두는 속성 이름.
 */
public final class SessionAttributes {

    /** 로그인한 시각. 절대 수명(정책 7.4 제2항) 계산에 쓴다 */
    public static final String AUTHENTICATED_AT = "auth.authenticatedAt";

    /** "브라우저 / 운영체제" 요약 */
    public static final String DEVICE = "auth.device";

    /** 비밀번호는 맞았고 일회용 코드를 기다리는 상태 */
    public static final String MFA_PENDING = "auth.mfaPending";

    private SessionAttributes() {
    }
}
