package com.back.group.application;

import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberHistory;
import com.back.group.domain.GroupMemberRepository;
import com.back.group.domain.GroupRepository;
import com.back.user.application.UserEvents;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하면 소속된 모든 그룹에서 빠지고 멤버 이력에 LEFT로 남는다 (정책 7.8 제2항).
 * 탈퇴와 같은 트랜잭션에서 처리한다. 보관 중인 그룹의 멤버 행은 영구 삭제 때까지 그대로 둔다 (정책 2.6 제4항).
 */
@Component
@RequiredArgsConstructor
public class GroupWithdrawalListener {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMembership membership;
    private final Clock clock;

    @EventListener
    public void on(UserEvents.Withdrawn event) {
        long userId = event.userId();
        Instant now = clock.instant();
        for (long groupId : memberRepository.findActiveGroupIds(userId)) {
            Group group = groupRepository.findForUpdate(groupId).orElseThrow();
            membership.depart(group, userId, GroupMemberHistory.Type.LEFT, userId, now);
        }
    }
}
