package com.back.group.application;

import com.back.common.api.PageDto;
import com.back.group.api.GroupDto;
import java.util.List;

/**
 * 그룹 목록·화면 조회 (MyBatis).
 */
public interface GroupQueryService {

    int MAX_PAGE_SIZE = 100;

    PageDto.CursorRes<GroupDto.SummaryRes> myGroups(long userId, Long cursor, int size);

    GroupDto.DetailRes detail(long userId, long groupId);

    List<GroupDto.MemberRes> members(long userId, long groupId);

    /** 멤버 이력은 증빙이므로 OWNER만 본다. */
    List<GroupDto.HistoryRes> history(long userId, long groupId);
}
