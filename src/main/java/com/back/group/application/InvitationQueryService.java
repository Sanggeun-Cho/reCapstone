package com.back.group.application;

import com.back.group.api.InvitationDto;
import java.util.List;

/**
 * 초대 목록 조회 (MyBatis). 만료된 초대는 대기 중으로 보지 않는다.
 */
public interface InvitationQueryService {

    /** 그룹의 대기 중인 초대. OWNER만 본다. */
    List<InvitationDto.Res> pendingOfGroup(long userId, long groupId);

    /** 내 이메일로 받은 대기 중인 초대. 삭제된 그룹의 초대는 빠진다. */
    List<InvitationDto.ReceivedRes> received(long userId);
}
