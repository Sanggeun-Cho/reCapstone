package com.back.auth.application;

/**
 * 본인 재확인 (정책 7.5). 인계 서명, 탈퇴처럼 무거운 행위 직전에 부른다.
 */
public interface ReauthService {

    /**
     * 2단계 인증을 켰으면 {@code code}(일회용 코드 또는 복구 코드), 아니면 {@code password}로 확인한다.
     * 실패하면 {@code REAUTH_FAILED}를 던지고 로그인 실패 횟수에 넣는다. 쓰기 트랜잭션 안에서 부른다.
     */
    void verify(long userId, String password, String code);
}
