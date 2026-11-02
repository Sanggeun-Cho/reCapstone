package com.back.group.application;

import com.back.common.authz.AccessPolicy;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberRepository;
import com.back.group.domain.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 그룹 권한. 멤버는 그룹 정보와 멤버 목록을 보고(정책 2.3 제3항), 관리는 OWNER가 한다 (정책 2.1, 2.3).
 * 삭제되어 보관 중인 그룹은 누구에게나 404다 (정책 4.3 제4항).
 */
@Component
@RequiredArgsConstructor
public class GroupAccessPolicy implements AccessPolicy<Group, GroupAccessPolicy.Action> {

    public enum Action {
        /** 그룹 상세, 멤버 목록 */
        VIEW,
        /** 이름 변경, 삭제, 양도, 초대, 멤버 제거, 멤버 이력 */
        MANAGE
    }

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    @Override
    public boolean can(long userId, Group group, Action action) {
        return switch (action) {
            case VIEW -> memberRepository.isMember(group.getId(), userId);
            case MANAGE -> group.isOwner(userId);
        };
    }

    /** 삭제되지 않은 그룹을 찾아 권한을 확인한다. */
    public Group checkAndGet(long userId, long groupId, Action action) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        check(userId, group, action);
        return group;
    }

    /** 쓰기용. 그룹 행을 잠근 뒤 권한을 확인한다. */
    public Group checkAndLock(long userId, long groupId, Action action) {
        Group group = groupRepository.findForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        check(userId, group, action);
        return group;
    }
}
