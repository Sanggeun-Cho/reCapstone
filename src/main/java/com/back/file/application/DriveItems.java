package com.back.file.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.handover.application.HandoverStatus;
import com.back.space.application.SpaceAccessPolicy;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 파일 서비스들이 함께 쓰는 항목 조회와 공통 검사.
 * 스페이스 밖의 항목, 휴지통에 있는 항목은 없는 것으로 보고 404로 답한다.
 */
@Component
@RequiredArgsConstructor
class DriveItems {

    private final DriveItemRepository repository;
    private final SpaceAccessPolicy accessPolicy;
    private final HandoverStatus handoverStatus;

    /** 휴지통에 있지 않은 항목 (자신과 조상 모두 휴지통 표시가 없음). */
    DriveItem active(long spaceId, long itemId) {
        return find(spaceId, itemId, found -> repository.isActive(itemId, spaceId));
    }

    /** 대상 폴더. {@code folderId}가 null이면 최상위(null). 파일이면 400 {@code INVALID_MOVE_TARGET}. */
    DriveItem folderOrRoot(long spaceId, Long folderId) {
        if (folderId == null) {
            return null;
        }
        DriveItem folder = active(spaceId, folderId);
        if (!folder.isFolder()) {
            throw new BusinessException(ErrorCode.INVALID_MOVE_TARGET);
        }
        return folder;
    }

    /** 휴지통 목록에 보이는 항목(직접 휴지통으로 옮긴 항목). 폴더와 함께 들어간 하위 항목은 404. */
    DriveItem trashed(long spaceId, long itemId) {
        return find(spaceId, itemId, DriveItem::isTrashed);
    }

    private DriveItem find(long spaceId, long itemId, Predicate<DriveItem> visible) {
        return repository.findByIdAndSpaceId(itemId, spaceId)
                .filter(visible)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 쓰기 전 공통 검사: 그룹 → 스페이스 행을 잠그고(S2와 같은 순서) 담당자인지 확인한 뒤, 인계가 진행 중이면 409
     * (정책 4.2, 5.2, 8.5 제6항). 스페이스 행 잠금이 같은 스페이스의 이름 결정을 한 줄로 세운다.
     */
    void lockForWrite(long userId, long spaceId) {
        accessPolicy.checkAndLock(userId, spaceId, SpaceAccessPolicy.Action.WRITE);
        requireNoHandover(spaceId);
    }

    /** 인계가 진행되는 동안에는 파일과 휴지통을 쓸 수 없다. 읽기는 허용한다. */
    void requireNoHandover(long spaceId) {
        if (handoverStatus.isInProgress(spaceId)) {
            throw new BusinessException(ErrorCode.HANDOVER_IN_PROGRESS);
        }
    }

    /**
     * 바뀐 이름을 DB에 반영한다. 이름은 스페이스 잠금 안에서 미리 확인하므로 유니크 인덱스에 걸리는 일은 없어야 하지만,
     * 걸리면 500이 아니라 {@code NAME_CONFLICT}로 답한다.
     */
    void flushNames() {
        try {
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT);
        }
    }

    static int depthUnder(DriveItem parent) {
        return parent == null ? 1 : parent.getDepth() + 1;
    }

    static Long idOf(DriveItem item) {
        return item == null ? null : item.getId();
    }
}
