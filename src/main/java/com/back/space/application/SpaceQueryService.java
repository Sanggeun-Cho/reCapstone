package com.back.space.application;

import com.back.common.api.PageDto;
import com.back.space.api.SpaceDto;
import java.util.List;

/**
 * 스페이스 목록·이력 조회 (MyBatis).
 */
public interface SpaceQueryService {

    int MAX_PAGE_SIZE = 100;

    /** 그룹의 스페이스 목록. OWNER만 본다 (정책 4.3 제2항). {@code page}는 1부터 센다. */
    PageDto.PageRes<SpaceDto.SummaryRes> groupSpaces(long userId, long groupId, int page, int size);

    /** 담당자 변경 이력. OWNER와 현재 담당자가 본다. */
    List<SpaceDto.AssignmentChangeRes> assignmentChanges(long userId, long spaceId);
}
