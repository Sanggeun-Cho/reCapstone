package com.back.handover.application;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 인계 절차(S5)가 생기기 전의 기본 구현. 진행 중인 인계는 없다.
 */
@Component
public class NoHandoverStatus implements HandoverStatus {

    @Override
    public Optional<Long> ongoingHandoverId(long spaceId) {
        return Optional.empty();
    }

    @Override
    public boolean anyInProgressInGroup(long groupId) {
        return false;
    }

    @Override
    public boolean isReceiver(long spaceId, long userId) {
        return false;
    }

    @Override
    public boolean isPartyInGroup(long groupId, long userId) {
        return false;
    }
}
