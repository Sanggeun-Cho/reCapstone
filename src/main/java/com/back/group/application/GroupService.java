package com.back.group.application;

import com.back.group.api.GroupDto;

/**
 * 그룹 쓰기 (정책 제2장). 목록·화면 조회는 {@link GroupQueryService}가 맡는다.
 */
public interface GroupService {

    GroupDto.Res create(long userId, GroupDto.NameReq req);

    GroupDto.Res rename(long userId, long groupId, GroupDto.NameReq req);

    /** 그룹 삭제 (정책 2.5). 그룹 행만 soft delete하고, 대기 중인 초대와 스페이스 삭제 요청을 무효로 만든다. */
    void delete(long userId, long groupId, GroupDto.DeleteReq req);

    /** OWNER 양도 (정책 2.1 제2항). 받는 사람의 수락 없이 즉시 바뀐다. */
    GroupDto.Res transferOwner(long userId, long groupId, GroupDto.TransferReq req);

    void removeMember(long userId, long groupId, long targetUserId);

    void leave(long userId, long groupId);
}
