package com.back.auth.mail;

import com.back.auth.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 인증 메일을 커밋 이후에 보낸다. 발송에 실패해도 이미 커밋된 요청은 성공으로 두고 로그만 남긴다 (재발송으로 복구).
 * 로그에는 토큰과 링크를 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthMailSender {

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    @TransactionalEventListener(fallbackExecution = true)
    public void send(AuthMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(mail.to());
        switch (mail) {
            case AuthMail.EmailVerification verification -> {
                message.setSubject("[인계점] 이메일 인증");
                message.setText("""
                        인계점 가입을 마치려면 아래 링크를 열어 이메일을 인증하세요.
                        링크는 24시간 동안 한 번만 쓸 수 있습니다.

                        %s

                        가입한 적이 없다면 이 메일을 무시하세요.
                        """.formatted(link("/verify-email", verification.token())));
            }
            case AuthMail.PasswordReset reset -> {
                message.setSubject("[인계점] 비밀번호 재설정");
                message.setText("""
                        아래 링크를 열어 새 비밀번호를 설정하세요.
                        링크는 30분 동안 한 번만 쓸 수 있습니다.

                        %s

                        요청한 적이 없다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다.
                        """.formatted(link("/reset-password", reset.token())));
            }
        }
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send auth mail: {}", mail.getClass().getSimpleName(), e);
        }
    }

    private String link(String path, String token) {
        return properties.frontendUrl() + path + "?token=" + token;
    }
}
