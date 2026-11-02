package com.back.auth.application;

/**
 * 비밀번호 재설정 (정책 7.6 제3항).
 */
public interface PasswordResetService {

    /** 활성 계정이면 재설정 링크를 메일로 보낸다. 가입 여부를 드러내지 않도록 결과를 돌려주지 않는다. */
    void request(String email);

    /** 비밀번호를 바꾸고, 모든 세션을 종료하고, 로그인 잠금을 푼다. 2단계 인증은 그대로 둔다. */
    void confirm(String token, String newPassword);
}
