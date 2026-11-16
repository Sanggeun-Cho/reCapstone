package com.back.group.application;

import com.back.auth.application.ReauthService;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.api.GroupDto;
import com.back.group.application.GroupAccessPolicy.Action;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberHistory;
import com.back.group.domain.GroupMemberRepository;
import com.back.group.domain.GroupRepository;
import com.back.group.domain.InvitationRepository;
import com.back.handover.application.HandoverStatus;
import com.back.space.domain.SpaceDeletionRequestRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final InvitationRepository invitationRepository;
    private final SpaceDeletionRequestRepository deletionRequestRepository;
    private final GroupAccessPolicy accessPolicy;
    private final GroupMembership membership;
    private final HandoverStatus handoverStatus;
    private final ReauthService reauthService;
    private final Clock clock;

    @Override
    @Transactional
    public GroupDto.Res create(long userId, GroupDto.NameReq req) {
        Group group = groupRepository.save(new Group(req.name().strip(), userId));
        membership.join(group.getId(), userId, userId, clock.instant());
        return GroupDto.Res.from(group);
    }

    @Override
    @Transactional
    public GroupDto.Res rename(long userId, long groupId, GroupDto.NameReq req) {
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        group.rename(req.name().strip());
        return GroupDto.Res.from(group);
    }

    @Override
    @Transactional
    public void delete(long userId, long groupId, GroupDto.DeleteReq req) {
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        if (handoverStatus.anyInProgressInGroup(groupId)) {
            throw new BusinessException(ErrorCode.HANDOVER_IN_PROGRESS);
        }
        if (!group.getName().equals(req.confirmName().strip())) {
            throw new BusinessException(ErrorCode.GROUP_NAME_MISMATCH);
        }
        reauthService.verify(userId, req.reauth());

        Instant now = clock.instant();
        group.delete(now);
        invitationRepository.cancelPendingOfGroup(groupId, now);
        deletionRequestRepository.invalidatePendingOfGroup(groupId, now);
    }

    @Override
    @Transactional
    public GroupDto.Res transferOwner(long userId, long groupId, GroupDto.TransferReq req) {
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        long newOwnerId = req.userId();
        if (newOwnerId == userId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신에게는 양도할 수 없습니다.");
        }
        if (!memberRepository.isMember(groupId, newOwnerId)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
        reauthService.verify(userId, req.reauth());

        group.transferOwner(newOwnerId);
        // 동의할 사람이 바뀌었으므로 대기 중인 스페이스 삭제 요청은 무효가 된다 (정책 3.4 제6항)
        deletionRequestRepository.invalidatePendingOfGroup(groupId, clock.instant());
        return GroupDto.Res.from(group);
    }

    @Override
    @Transactional
    public void removeMember(long userId, long groupId, long targetUserId) {
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.MANAGE);
        membership.depart(group, targetUserId, GroupMemberHistory.Type.REMOVED, userId, clock.instant());
    }

    @Override
    @Transactional
    public void leave(long userId, long groupId) {
        Group group = accessPolicy.checkAndLock(userId, groupId, Action.VIEW);
        membership.depart(group, userId, GroupMemberHistory.Type.LEFT, userId, clock.instant());
    }
}
