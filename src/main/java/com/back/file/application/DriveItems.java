package com.back.file.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.handover.application.HandoverStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 파일 서비스들이 함께 쓰는 항목 조회와 공통 검사.
 * 스페이스 밖의 항목, 휴지통에 있는 항목은 없는 것으로 보고 404로 답한다.
 */
@Component
@RequiredArgsConstructor
class DriveItems {

    private final DriveItemRepository repository;
    private final HandoverStatus handoverStatus;

    /** 휴지통에 있지 않은 항목. */
    DriveItem active(long spaceId, long itemId) {
        DriveItem item = repository.findByIdAndSpaceId(itemId, spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!repository.hasNoTrashedAncestor(itemId, spaceId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return item;
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
        DriveItem item = repository.findByIdAndSpaceId(itemId, spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.isTrashed()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    /** 인계가 진행되는 동안에는 파일과 휴지통을 쓸 수 없다 (정책 4.2, 5.2, 8.5 제6항). */
    void requireNoHandover(long spaceId) {
        if (handoverStatus.isInProgress(spaceId)) {
            throw new BusinessException(ErrorCode.HANDOVER_IN_PROGRESS);
        }
    }

    static int depthUnder(DriveItem parent) {
        return parent == null ? 1 : parent.getDepth() + 1;
    }

    static Long idOf(DriveItem item) {
        return item == null ? null : item.getId();
    }
}
