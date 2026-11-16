package com.back.handover.application;

import java.util.Optional;

/**
 * 진행 중인 인계 상태를 묻는 확장 지점. 그룹·스페이스(S2)는 이 인터페이스로만 인계 상태를 확인한다.
 * <p>
 * 인계 절차는 S5에서 구현한다. 그 전까지는 {@link NoHandoverStatus}가 "진행 중인 인계 없음"으로 답한다.
 */
public interface HandoverStatus {

    /** 스페이스에서 진행 중인 인계의 id. */
    Optional<Long> ongoingHandoverId(long spaceId);

    default boolean isInProgress(long spaceId) {
        return ongoingHandoverId(spaceId).isPresent();
    }

    /** 그룹의 스페이스 중 인계가 진행 중인 곳이 있는지 (그룹 삭제 차단, 정책 2.5 제2항). */
    boolean anyInProgressInGroup(long groupId);

    /** 스페이스에서 진행 중인 인계의 인수자인지 (정책 4.1). */
    boolean isReceiver(long spaceId, long userId);

    /** 그룹 안에서 진행 중인 인계의 인계자나 인수자인지 (멤버 이탈 차단, 정책 2.3 제2항). */
    boolean isPartyInGroup(long groupId, long userId);
}
