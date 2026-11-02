package com.back.space.application;

import com.back.common.authz.AccessPolicy;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.domain.Group;
import com.back.group.domain.GroupRepository;
import com.back.handover.application.HandoverStatus;
import com.back.space.domain.Space;
import com.back.space.domain.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 스페이스 권한 (정책 4.1, 4.2). 역할을 따로 저장하지 않고 상태에서 계산한다.
 * <ul>
 *   <li>관리자: 그룹의 OWNER</li>
 *   <li>담당자: {@code space.assignee_id}</li>
 *   <li>인수자: 진행 중인 인계의 인수자 ({@link HandoverStatus}, S5)</li>
 * </ul>
 * 삭제되어 보관 중인 스페이스나, 삭제된 그룹의 스페이스는 누구에게나 404다 (정책 4.3 제4항).
 */
@Component
@RequiredArgsConstructor
public class SpaceAccessPolicy implements AccessPolicy<SpaceAccessPolicy.Target, SpaceAccessPolicy.Action> {

    public enum Action {
        /** 스페이스 상세와 자료 읽기 */
        READ,
        /** 이름 변경, 담당자 지정, 긴급 재지정 (정책 3.1, 3.3) */
        MANAGE,
        /** 자료 작성·수정 (정책 4.2 제2항: 담당자만) */
        WRITE,
        /** 삭제 요청·승인·거절·철회 */
        DELETE_REQUEST,
        /** 담당자 변경 이력 조회. OWNER와 현재 담당자. 이전 담당자는 접근 권한을 잃었으므로 보지 않는다 */
        VIEW_HISTORY
    }

    /** 판정 대상. 스페이스와 그 그룹, 그리고 진행 중인 인계의 인수자인지. */
    public record Target(Space space, Group group, boolean receiver) {

        boolean isOwner(long userId) {
            return group.isOwner(userId);
        }

        boolean isAssignee(long userId) {
            return space.isAssignee(userId);
        }
    }

    private final SpaceRepository spaceRepository;
    private final GroupRepository groupRepository;
    private final HandoverStatus handoverStatus;

    @Override
    public boolean can(long userId, Target target, Action action) {
        return switch (action) {
            case READ -> target.isOwner(userId) || target.isAssignee(userId) || target.receiver();
            case MANAGE -> target.isOwner(userId);
            case WRITE -> target.isAssignee(userId);
            case DELETE_REQUEST -> target.isOwner(userId) || target.isAssignee(userId);
            case VIEW_HISTORY -> target.isOwner(userId) || target.isAssignee(userId);
        };
    }

    public Target checkAndGet(long userId, long spaceId, Action action) {
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Group group = groupRepository.findById(space.getGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return check(userId, space, group, action);
    }

    /**
     * 쓰기용. 그룹 행, 스페이스 행 순서로 잠근 뒤 권한을 확인한다.
     * <p>
     * 멤버 제거·OWNER 양도·그룹 삭제는 그룹 행을 잠그므로, 스페이스 쓰기도 그룹 행을 먼저 잠가야 서로를 기다린다.
     * 그렇지 않으면 제거와 담당자 지정이 엇갈려 복합 FK 위반(500)이 나거나, 양도와 겹친 삭제 요청이 무효 처리를 피한다
     * (정책 3.4 제6항). 잠금 순서를 항상 그룹 → 스페이스로 맞춰 교착도 피한다.
     * 스페이스 엔티티는 잠근 뒤에 처음 읽어, 잠그기 전에 읽은 낡은 상태로 판정하지 않는다.
     */
    public Target checkAndLock(long userId, long spaceId, Action action) {
        long groupId = spaceRepository.findGroupId(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Group group = groupRepository.findForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Space space = spaceRepository.findForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return check(userId, space, group, action);
    }

    private Target check(long userId, Space space, Group group, Action action) {
        Target target = new Target(space, group, handoverStatus.isReceiver(space.getId(), userId));
        check(userId, target, action);
        return target;
    }
}
