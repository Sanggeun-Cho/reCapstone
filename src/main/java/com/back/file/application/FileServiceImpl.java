package com.back.file.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.file.FileProperties;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.file.storage.FileStorage;
import com.back.file.support.ItemNames;
import com.back.space.application.SpaceAccessPolicy;
import com.back.space.application.SpaceAccessPolicy.Action;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폴더와 항목 쓰기. 쓰기는 모두 그룹 → 스페이스 행을 잠근 뒤(S2와 같은 순서) 담당자인지, 인계가 진행 중인지 확인한다.
 * 스페이스 행 잠금이 같은 스페이스의 이름 결정을 한 줄로 세운다. 유니크 인덱스는 마지막 안전장치다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileServiceImpl implements FileService {

    private final DriveItemRepository repository;
    private final SpaceAccessPolicy accessPolicy;
    private final DriveItems items;
    private final FileStorage storage;
    private final FileProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public long createFolder(long userId, long spaceId, FileDto.FolderReq req) {
        items.lockForWrite(userId, spaceId);
        DriveItem parent = items.folderOrRoot(spaceId, req.parentId());
        int depth = DriveItems.depthUnder(parent);
        if (depth > DriveItem.MAX_FOLDER_DEPTH) {
            throw new BusinessException(ErrorCode.FOLDER_DEPTH_EXCEEDED);
        }
        String name = ItemNames.validate(req.name());
        requireNameFree(spaceId, DriveItems.idOf(parent), name, null);
        DriveItem folder = repository.save(DriveItem.folder(spaceId, DriveItems.idOf(parent), depth, name));
        items.flushNames();
        return folder.getId();
    }

    @Override
    @Transactional
    public long rename(long userId, long spaceId, long itemId, FileDto.RenameReq req) {
        items.lockForWrite(userId, spaceId);
        DriveItem item = items.active(spaceId, itemId);
        String name = ItemNames.validate(req.name());
        requireNameFree(spaceId, item.getParentId(), name, item.getId());
        item.rename(name);
        items.flushNames();
        return itemId;
    }

    @Override
    @Transactional
    public long move(long userId, long spaceId, long itemId, FileDto.MoveReq req) {
        items.lockForWrite(userId, spaceId);
        DriveItem item = items.active(spaceId, itemId);
        DriveItem target = items.folderOrRoot(spaceId, req.targetFolderId());
        Long targetId = DriveItems.idOf(target);
        if (Objects.equals(targetId, item.getParentId())) {
            return itemId;
        }
        // 폴더를 자기 자신이나 그 하위 폴더로 옮길 수 없다 (정책 8.3 제4항)
        if (item.isFolder() && target != null && repository.ancestorIds(target.getId()).contains(item.getId())) {
            throw new BusinessException(ErrorCode.INVALID_MOVE_TARGET);
        }
        int depth = DriveItems.depthUnder(target);
        // 휴지통 항목은 트리에서 떼어 두므로 세지 않는다. 대신 복구할 때 넘치면 최상위로 돌린다
        if (item.isFolder() && depth + repository.folderHeight(itemId) > DriveItem.MAX_FOLDER_DEPTH) {
            throw new BusinessException(ErrorCode.FOLDER_DEPTH_EXCEEDED);
        }
        requireNameFree(spaceId, targetId, item.getName(), item.getId());
        int delta = depth - item.getDepth();
        item.moveTo(targetId, depth);
        items.flushNames();
        if (delta != 0) {
            repository.shiftDescendantDepth(itemId, delta);
        }
        return itemId;
    }

    @Override
    @Transactional
    public FileDto.TrashedRes trash(long userId, long spaceId, long itemId) {
        items.lockForWrite(userId, spaceId);
        DriveItem item = items.active(spaceId, itemId);
        int moved = repository.subtreeIds(itemId).size();
        item.trash(userId, clock.instant());
        return new FileDto.TrashedRes(moved);
    }

    @Override
    public FileDto.DownloadUrlRes downloadUrl(long userId, long spaceId, long itemId, FileDto.DownloadUrlReq req) {
        accessPolicy.checkAndGet(userId, spaceId, Action.READ);
        DriveItem file = items.active(spaceId, itemId);
        if (!file.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "폴더는 내려받을 수 없습니다.");
        }
        FileDto.Disposition disposition = req == null ? FileDto.Disposition.ATTACHMENT : req.dispositionOrDefault();
        if (disposition == FileDto.Disposition.INLINE && !file.isInlineViewable()) {
            throw new BusinessException(ErrorCode.INLINE_NOT_ALLOWED);
        }
        Instant expiresAt = clock.instant().plus(properties.presignTtl());
        String url = storage.presignGet(file.getStorageKey(), contentDisposition(disposition, file.getName()),
                file.getContentType(), properties.presignTtl()).toString();
        return new FileDto.DownloadUrlRes(url, expiresAt);
    }

    /** {@code attachment; filename*=UTF-8''...} (RFC 6266, RFC 5987). 한글 이름이 깨지지 않는다 (NFR-02). */
    static String contentDisposition(FileDto.Disposition disposition, String name) {
        String type = disposition == FileDto.Disposition.INLINE ? "inline" : "attachment";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return type + "; filename*=UTF-8''" + encoded;
    }

    /** 이름 변경·이동·폴더 생성에서는 겹치면 거부한다 (정책 8.3 제3항). 자기 자신과는 겹치지 않는다. */
    private void requireNameFree(long spaceId, Long parentId, String name, Long selfId) {
        repository.findByName(spaceId, parentId, ItemNames.key(name))
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.NAME_CONFLICT);
                });
    }
}
