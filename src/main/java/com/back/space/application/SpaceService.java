package com.back.space.application;

import com.back.space.api.SpaceDto;

/**
 * 스페이스와 담당자 (정책 제3장). 목록·이력 조회는 {@link SpaceQueryService}가 맡는다.
 */
public interface SpaceService {

    SpaceDto.Res create(long userId, long groupId, SpaceDto.NameReq req);

    SpaceDto.DetailRes detail(long userId, long spaceId);

    SpaceDto.Res rename(long userId, long spaceId, SpaceDto.NameReq req);

    /** 담당자가 없는 스페이스에 지정한다. 긴급 재지정으로 비운 적이 있으면 공석 충원(REFILL), 아니면 최초 지정(INITIAL). */
    SpaceDto.Res assign(long userId, long spaceId, SpaceDto.AssigneeReq req);

    /** 긴급 재지정 (정책 3.3). 진행 중인 인계가 있어도 실행하며, 그 인계의 취소는 S5에서 붙인다. */
    SpaceDto.Res forceReassign(long userId, long spaceId, SpaceDto.ForcedReassignmentReq req);
}
