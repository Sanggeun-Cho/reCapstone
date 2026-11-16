package com.back.group.mail;

import com.back.auth.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 초대 메일을 커밋 이후에 보낸다 (정책 2.4 제1항). 롤백된 초대는 메일을 보내지 않는다.
 * 발송에 실패해도 초대는 유효하며, 받은 사람은 받은 초대 목록에서 확인할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationMailSender {

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    @TransactionalEventListener
    public void send(InvitationMail mail) {
        String link = properties.frontendUrl() + (mail.registered() ? "/invitations" : "/signup");
        String guide = mail.registered()
                ? "아래 링크에서 받은 초대를 확인하고 수락하세요."
                : "아래 링크에서 이 이메일로 가입하고 이메일 인증을 마친 뒤, 받은 초대 목록에서 수락하세요.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(mail.to());
        message.setSubject("[인계점] " + mail.groupName() + " 그룹 초대");
        message.setText("""
                '%s' 그룹에서 초대했습니다.
                %s
                초대는 7일 동안 유효합니다.

                %s
                """.formatted(mail.groupName(), guide, link));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send invitation mail", e);
        }
    }
}
