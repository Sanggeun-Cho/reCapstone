package com.back.space.application;

import com.back.common.api.PageDto;
import com.back.group.api.GroupDto;
import com.back.group.application.GroupAccessPolicy;
import com.back.handover.application.HandoverStatus;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceAccessPolicy.Action;
import com.back.space.query.SpaceQueryMapper;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryServiceImpl implements SpaceQueryService {

    private final SpaceQueryMapper mapper;
    private final GroupAccessPolicy groupAccessPolicy;
    private final SpaceAccessPolicy accessPolicy;
    private final HandoverStatus handoverStatus;
    private final Clock clock;

    @Override
    public PageDto.PageRes<SpaceDto.SummaryRes> groupSpaces(long userId, long groupId, int page, int size) {
        groupAccessPolicy.checkAndGet(userId, groupId, GroupAccessPolicy.Action.MANAGE);
        long total = mapper.countGroupSpaces(groupId);
        long offset = (long) (page - 1) * size;
        // 진행 중인 인계 id는 S5에서 같은 SQL로 읽는다. 그 전까지 확장 지점은 항상 비어 있다
        List<SpaceDto.SummaryRes> content = mapper.findGroupSpaces(groupId, offset, size, clock.instant()).stream()
                .map(row -> new SpaceDto.SummaryRes(row.id(), row.name(),
                        row.assigneeId() == null ? null : new GroupDto.UserRef(row.assigneeId(), row.assigneeName()),
                        handoverStatus.ongoingHandoverId(row.id()).orElse(null),
                        row.deletionPending()))
                .toList();
        return PageDto.PageRes.of(content, page, size, total);
    }

    @Override
    public List<SpaceDto.AssignmentChangeRes> assignmentChanges(long userId, long spaceId) {
        accessPolicy.checkAndGet(userId, spaceId, Action.DELETE_REQUEST);
        return mapper.findAssignmentChanges(spaceId, GroupDto.WITHDRAWN_USER_NAME).stream()
                .map(row -> new SpaceDto.AssignmentChangeRes(row.id(), row.type(),
                        ref(row.fromUserId(), row.fromUserName()), ref(row.toUserId(), row.toUserName()),
                        new GroupDto.UserRef(row.actorId(), row.actorName()), row.reason(), row.occurredAt()))
                .toList();
    }

    private static GroupDto.UserRef ref(Long id, String name) {
        return id == null ? null : new GroupDto.UserRef(id, name);
    }
}
