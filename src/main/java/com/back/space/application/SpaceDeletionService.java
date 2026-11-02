package com.back.space.application;

import com.back.space.api.SpaceDto;

/**
 * 스페이스 삭제 요청 (정책 3.4). OWNER와 담당자가 모두 동의해야 삭제된다.
 */
public interface SpaceDeletionService {

    /** 삭제를 요청한다. 동의할 상대가 없으면(담당자가 없거나 OWNER가 겸함) 바로 삭제한다. */
    SpaceDto.DeletionResultRes request(long userId, long spaceId, SpaceDto.DeletionReq req);

    SpaceDto.DeletionRequestRes pending(long userId, long spaceId);

    void approve(long userId, long spaceId, SpaceDto.DeletionReq req);

    void reject(long userId, long spaceId, SpaceDto.RejectReq req);

    /** 요청한 사람이 승인 전에 철회한다. */
    void withdraw(long userId, long spaceId);
}
