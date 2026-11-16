package com.back.group.application;

import com.back.group.api.GroupDto;
import com.back.group.api.InvitationDto;

/**
 * 그룹 초대 쓰기 (정책 2.4). 목록 조회는 {@link InvitationQueryService}가 맡는다.
 */
public interface InvitationService {

    /** 초대한다. 같은 이메일의 대기 중인 초대는 대체된다. 메일은 커밋한 뒤에 보낸다. */
    InvitationDto.Res invite(long userId, long groupId, InvitationDto.CreateReq req);

    void cancel(long userId, long groupId, long invitationId);

    GroupDto.Res accept(long userId, long invitationId);

    void decline(long userId, long invitationId);
}
