package com.back.group.application;

import com.back.common.api.PageDto;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.api.GroupDto;
import com.back.group.application.GroupAccessPolicy.Action;
import com.back.group.query.GroupQueryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupQueryServiceImpl implements GroupQueryService {

    private final GroupQueryMapper mapper;
    private final GroupAccessPolicy accessPolicy;

    @Override
    public PageDto.CursorRes<GroupDto.SummaryRes> myGroups(long userId, Long cursor, int size) {
        List<GroupDto.SummaryRes> fetched = mapper.findMyGroups(userId, cursor, size + 1).stream()
                .map(row -> new GroupDto.SummaryRes(row.id(), row.name(), row.owner()))
                .toList();
        return PageDto.CursorRes.of(fetched, size, GroupDto.SummaryRes::id);
    }

    @Override
    public GroupDto.DetailRes detail(long userId, long groupId) {
        accessPolicy.checkAndGet(userId, groupId, Action.VIEW);
        GroupQueryMapper.DetailRow row = mapper.findDetail(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new GroupDto.DetailRes(row.id(), row.name(), new GroupDto.UserRef(row.ownerId(), row.ownerName()),
                row.memberCount());
    }

    @Override
    public List<GroupDto.MemberRes> members(long userId, long groupId) {
        accessPolicy.checkAndGet(userId, groupId, Action.VIEW);
        return mapper.findMembers(groupId).stream()
                .map(row -> new GroupDto.MemberRes(row.userId(), row.name(), row.email(), row.workPhone(),
                        row.owner(), row.joinedAt()))
                .toList();
    }

    @Override
    public List<GroupDto.HistoryRes> history(long userId, long groupId) {
        accessPolicy.checkAndGet(userId, groupId, Action.MANAGE);
        return mapper.findHistory(groupId, GroupDto.WITHDRAWN_USER_NAME).stream()
                .map(row -> new GroupDto.HistoryRes(new GroupDto.UserRef(row.userId(), row.userName()), row.type(),
                        new GroupDto.UserRef(row.actorId(), row.actorName()), row.occurredAt()))
                .toList();
    }
}
