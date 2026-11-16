package com.back.group.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMember;
import com.back.group.domain.GroupMemberHistory;
import com.back.group.domain.GroupMemberHistoryRepository;
import com.back.group.domain.GroupMemberRepository;
import com.back.handover.application.HandoverStatus;
import com.back.space.domain.SpaceRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 그룹 멤버의 가입과 이탈. 멤버 행과 멤버 이력을 항상 함께 바꾼다 (정책 2.3 제4항).
 * 호출한 쪽의 쓰기 트랜잭션 안에서 쓴다.
 */
@Component
@RequiredArgsConstructor
public class GroupMembership {

    private final GroupMemberRepository memberRepository;
    private final GroupMemberHistoryRepository historyRepository;
    private final SpaceRepository spaceRepository;
    private final HandoverStatus handoverStatus;

    public void join(long groupId, long userId, long actorId, Instant at) {
        memberRepository.save(new GroupMember(groupId, userId, at));
        historyRepository.save(new GroupMemberHistory(groupId, userId, GroupMemberHistory.Type.JOINED, actorId, at));
    }

    /**
     * 멤버가 떠나거나 제거된다. OWNER, 담당자, 진행 중인 인계 당사자는 떠날 수 없다 (정책 2.3 제2항).
     *
     * @param type {@code LEFT}(스스로) 또는 {@code REMOVED}(OWNER가 제거)
     */
    public void depart(Group group, long userId, GroupMemberHistory.Type type, long actorId, Instant at) {
        long groupId = group.getId();
        if (!memberRepository.isMember(groupId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (group.isOwner(userId)) {
            throw new BusinessException(ErrorCode.OWNER_MUST_TRANSFER);
        }
        if (spaceRepository.existsActiveAssignment(groupId, userId) || handoverStatus.isPartyInGroup(groupId, userId)) {
            throw new BusinessException(ErrorCode.MEMBER_HAS_RESPONSIBILITY);
        }
        spaceRepository.clearArchivedAssignments(groupId, userId);
        memberRepository.deleteById(new GroupMember.Id(groupId, userId));
        historyRepository.save(new GroupMemberHistory(groupId, userId, type, actorId, at));
    }

    /**
     * 삭제되어 보관 중인 그룹에서 탈퇴자를 뺀다. 그룹이 복구되어도 탈퇴자가 멤버나 담당자로 돌아오지 않게 한다 (정책 2.6 제4항).
     * 보관 중인 그룹의 지위는 탈퇴를 막지 않으므로(정책 7.8 제1항) 담당 여부를 검사하지 않고 비운다.
     * 호출한 쪽이 그룹 행을 잠근 상태여야 한다.
     */
    public void departArchived(long groupId, long userId, Instant at) {
        spaceRepository.clearAllAssignmentsInGroup(groupId, userId);
        memberRepository.deleteById(new GroupMember.Id(groupId, userId));
        historyRepository.save(new GroupMemberHistory(groupId, userId, GroupMemberHistory.Type.LEFT, userId, at));
    }
}
