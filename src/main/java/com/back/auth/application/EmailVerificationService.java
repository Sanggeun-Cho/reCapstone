package com.back.auth.application;

import com.back.auth.api.AuthDto;

/**
 * 가입 이메일 인증 (정책 7.1 제4항).
 */
public interface EmailVerificationService {

    /** 인증 링크를 새로 발급해 메일로 보낸다(커밋 이후). 이전 링크는 무효가 된다. */
    void send(long userId, String email);

    AuthDto.EmailVerificationRes verify(String token);

    /** 인증 대기 중인 이메일에만 다시 보낸다. 가입 여부를 드러내지 않도록 결과를 돌려주지 않는다. */
    void resend(String email);
}
