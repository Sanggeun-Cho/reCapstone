package com.back.group.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.GroupProperties;
import com.back.group.api.GroupDto;
import com.back.group.api.InvitationDto;
import com.back.group.application.GroupAccessPolicy.Action;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberRepository;
import com.back.group.domain.GroupRepository;
import com.back.group.domain.Invitation;
import com.back.group.domain.InvitationRepository;
import com.back.group.mail.InvitationMail;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupAccessPolicy accessPolicy;
    private final GroupMembership membership;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public InvitationDto.Res invite(long userId, long groupId, InvitationDto.CreateReq req) {
        // 그룹 행을 잠가 같은 이메일을 동시에 초대해도 대기 중인 초대가 1건만 남게 한다
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        String email = User.normalizeEmail(req.email());
        var invitee = userRepository.findByEmail(email);
        if (invitee.isPresent() && memberRepository.isMember(groupId, invitee.get().getId())) {
            throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
        }

        Instant now = clock.instant();
        invitationRepository.findByGroupIdAndEmailAndStatus(groupId, email, Invitation.Status.PENDING)
                .ifPresent(previous -> {
                    previous.replace(now);
                    // 부분 유니크 인덱스가 새 초대와 충돌하지 않도록 먼저 반영한다
                    invitationRepository.flush();
                });
        Invitation invitation = invitationRepository.save(
                new Invitation(groupId, email, userId, now.plus(properties.invitationTtl())));

        eventPublisher.publishEvent(new InvitationMail(email, group.getName(), invitee.isPresent()));
        return InvitationDto.Res.from(invitation);
    }

    @Override
    @Transactional
    public void cancel(long userId, long groupId, long invitationId) {
        accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        Invitation invitation = invitationRepository.findById(invitationId)
                .filter(found -> found.getGroupId() == groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        requirePending(invitation, now);
        invitation.cancel(now);
    }

    @Override
    @Transactional
    public GroupDto.Res accept(long userId, long invitationId) {
        Invitation invitation = findMine(userId, invitationId);
        // 그룹이 삭제되었으면(보관 중) 초대도 무효다 (정책 2.5 제4항)
        Group group = groupRepository.findForUpdate(invitation.getGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_PENDING));
        Instant now = clock.instant();
        requirePending(invitation, now);
        if (memberRepository.isMember(group.getId(), userId)) {
            throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
        }
        invitation.accept(now);
        membership.join(group.getId(), userId, userId, now);
        return GroupDto.Res.from(group);
    }

    @Override
    @Transactional
    public void decline(long userId, long invitationId) {
        Invitation invitation = findMine(userId, invitationId);
        Instant now = clock.instant();
        requirePending(invitation, now);
        if (groupRepository.findById(invitation.getGroupId()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        invitation.decline(now);
    }

    /** 초대받은 이메일로 로그인한 사람만 응답할 수 있다. 남의 초대는 있는지도 알려 주지 않는다 (404). */
    private Invitation findMine(long userId, long invitationId) {
        String email = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED))
                .getEmail();
        return invitationRepository.findById(invitationId)
                .filter(invitation -> invitation.getEmail().equals(email))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void requirePending(Invitation invitation, Instant now) {
        if (!invitation.isPending(now)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
    }
}
