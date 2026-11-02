package com.back.support;

import com.back.handover.application.HandoverStatus;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인계 절차(S5)가 생기기 전에 "진행 중인 인계"를 흉내 내는 테스트 대역. 기본은 진행 중인 인계가 없다.
 */
public class FakeHandoverStatus implements HandoverStatus {

    private record Ongoing(long handoverId, long groupId, long giverId, long receiverId) {
    }

    private final Map<Long, Ongoing> bySpace = new ConcurrentHashMap<>();

    /** 스페이스에서 인계가 진행 중인 것으로 만든다. */
    public void start(long spaceId, long groupId, long giverId, long receiverId) {
        bySpace.put(spaceId, new Ongoing(900_000 + spaceId, groupId, giverId, receiverId));
    }

    public void reset() {
        bySpace.clear();
    }

    @Override
    public Optional<Long> ongoingHandoverId(long spaceId) {
        return Optional.ofNullable(bySpace.get(spaceId)).map(Ongoing::handoverId);
    }

    @Override
    public boolean anyInProgressInGroup(long groupId) {
        return bySpace.values().stream().anyMatch(ongoing -> ongoing.groupId() == groupId);
    }

    @Override
    public boolean isReceiver(long spaceId, long userId) {
        Ongoing ongoing = bySpace.get(spaceId);
        return ongoing != null && ongoing.receiverId() == userId;
    }

    @Override
    public boolean isPartyInGroup(long groupId, long userId) {
        return bySpace.values().stream().anyMatch(ongoing -> ongoing.groupId() == groupId
                && Set.of(ongoing.giverId(), ongoing.receiverId()).contains(userId));
    }
}
