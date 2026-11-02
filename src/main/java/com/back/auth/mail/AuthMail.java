package com.back.auth.mail;

/**
 * 커밋 이후에 보낼 인증 메일. 트랜잭션 안에서 발행하고 {@link AuthMailSender}가 받는다.
 */
public sealed interface AuthMail {

    String to();

    String token();

    record EmailVerification(String to, String token) implements AuthMail {
    }

    record PasswordReset(String to, String token) implements AuthMail {
    }
}
