package com.back.auth.application;

import com.back.auth.api.AuthDto;

/**
 * 로그인 (정책 7.2). 세션을 만드는 일은 웹 계층({@code SessionLogin})이 맡고, 여기서는 자격 증명만 확인한다.
 */
public interface AuthService {

    /**
     * 이메일과 비밀번호를 확인한다. 2단계 인증을 켠 사용자면 {@code mfaRequired}가 true인 결과를 돌려준다.
     */
    LoginResult login(String email, String password);

    /**
     * 로그인 2단계. 일회용 코드나 복구 코드를 확인한다.
     */
    AuthDto.UserSummary completeMfa(long userId, String code);

    record LoginResult(long userId, boolean mfaRequired, AuthDto.UserSummary user) {
    }
}
