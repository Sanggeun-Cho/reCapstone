package com.back.space.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.api.GroupDto;
import com.back.group.application.GroupAccessPolicy;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberRepository;
import com.back.handover.application.HandoverStatus;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceAccessPolicy.Action;
import com.back.space.application.SpaceAccessPolicy.Target;
import com.back.space.domain.AssignmentChange;
import com.back.space.domain.AssignmentChangeRepository;
import com.back.space.domain.Space;
import com.back.space.domain.SpaceDeletionRequestRepository;
import com.back.space.domain.SpaceRepository;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceServiceImpl implements SpaceService {

    private final SpaceRepository spaceRepository;
    private final AssignmentChangeRepository changeRepository;
    private final SpaceDeletionRequestRepository deletionRequestRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupAccessPolicy groupAccessPolicy;
    private final SpaceAccessPolicy accessPolicy;
    private final HandoverStatus handoverStatus;
    private final Clock clock;

    @Override
    @Transactional
    public SpaceDto.Res create(long userId, long groupId, SpaceDto.NameReq req) {
        // 그룹 삭제와 엇갈려 삭제된 그룹에 스페이스가 생기지 않도록 그룹 행을 잠근다
        groupAccessPolicy.checkAndLock(userId, groupId, GroupAccessPolicy.Action.MANAGE);
        Space space = spaceRepository.save(new Space(groupId, req.name().strip()));
        return toRes(space);
    }

    @Override
    public SpaceDto.DetailRes detail(long userId, long spaceId) {
        Target target = accessPolicy.checkAndGet(userId, spaceId, Action.READ);
        Space space = target.space();
        Group group = target.group();
        return new SpaceDto.DetailRes(space.getId(), space.getName(), GroupDto.Res.from(group),
                userRef(space.getAssigneeId()));
    }

    @Override
    @Transactional
    public SpaceDto.Res rename(long userId, long spaceId, SpaceDto.NameReq req) {
        Space space = accessPolicy.checkAndLock(userId, spaceId, Action.MANAGE).space();
        requireNoHandover(spaceId);
        space.rename(req.name().strip());
        return toRes(space);
    }

    @Override
    @Transactional
    public SpaceDto.Res assign(long userId, long spaceId, SpaceDto.AssigneeReq req) {
        Space space = accessPolicy.checkAndLock(userId, spaceId, Action.MANAGE).space();
        requireNoHandover(spaceId);
        if (space.hasAssignee()) {
            throw new BusinessException(ErrorCode.ASSIGNEE_ALREADY_SET);
        }
        requireMember(space.getGroupId(), req.userId());

        AssignmentChange.Type type = changeRepository.existsBySpaceIdAndType(spaceId, AssignmentChange.Type.FORCED)
                ? AssignmentChange.Type.REFILL
                : AssignmentChange.Type.INITIAL;
        changeAssignee(space, type, req.userId(), userId, null);
        return toRes(space);
    }

    @Override
    @Transactional
    public SpaceDto.Res forceReassign(long userId, long spaceId, SpaceDto.ForcedReassignmentReq req) {
        Space space = accessPolicy.checkAndLock(userId, spaceId, Action.MANAGE).space();
        if (!space.hasAssignee()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "담당자가 없는 스페이스입니다. 담당자 지정을 하세요.");
        }
        Long to = req.userId();
        if (to != null) {
            if (space.isAssignee(to)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 그 사람이 담당자입니다.");
            }
            requireMember(space.getGroupId(), to);
        }
        changeAssignee(space, AssignmentChange.Type.FORCED, to, userId, req.reason().strip());
        return toRes(space);
    }

    private void changeAssignee(Space space, AssignmentChange.Type type, Long to, long actorId, String reason) {
        Instant now = clock.instant();
        changeRepository.save(space.changeAssignee(type, to, actorId, reason, now));
        // 동의할 사람이 바뀌었으므로 대기 중인 삭제 요청은 무효가 된다 (정책 3.4 제6항)
        deletionRequestRepository.invalidatePendingOfSpace(space.getId(), now);
    }

    private void requireNoHandover(long spaceId) {
        if (handoverStatus.isInProgress(spaceId)) {
            throw new BusinessException(ErrorCode.HANDOVER_IN_PROGRESS);
        }
    }

    private void requireMember(long groupId, long userId) {
        if (!memberRepository.isMember(groupId, userId)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    private SpaceDto.Res toRes(Space space) {
        return new SpaceDto.Res(space.getId(), space.getName(), userRef(space.getAssigneeId()));
    }

    private GroupDto.UserRef userRef(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> new GroupDto.UserRef(user.getId(), user.getName()))
                .orElse(null);
    }
}
