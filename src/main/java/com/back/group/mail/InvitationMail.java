package com.back.group.mail;

/**
 * 커밋 이후에 보낼 초대 메일. 트랜잭션 안에서 발행하고 {@link InvitationMailSender}가 받는다.
 *
 * @param registered 그 이메일로 가입한 계정이 있는지. 링크를 받은 초대 화면과 가입 화면 중에서 고른다
 */
public record InvitationMail(String to, String groupName, boolean registered) {
}
