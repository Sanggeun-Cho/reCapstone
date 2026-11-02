package com.back.user.application;

import com.back.user.api.UserDto;

/**
 * 회원 계정: 가입, 내 정보, 비밀번호 변경, 탈퇴 (정책 7.1, 7.6, 7.8).
 */
public interface UserService {

    /** 인증 대기 계정을 만들고 인증 메일을 보낸다. */
    UserDto.SignupRes signup(UserDto.SignupReq req);

    UserDto.DetailRes getMe(long userId);

    UserDto.DetailRes updateMe(long userId, UserDto.UpdateReq req);

    /** 현재 비밀번호를 확인하고 바꾼다. 커밋 이후 {@code currentSessionId}를 뺀 세션을 모두 종료한다. */
    void changePassword(long userId, UserDto.ChangePasswordReq req, String currentSessionId);

    /** 본인 재확인과 탈퇴 차단 조건을 확인한 뒤 탈퇴한다. 커밋 이후 모든 세션을 종료한다. */
    void withdraw(long userId, String password, String code);
}
