package com.back.file.application;

import com.back.file.FileProperties;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.file.support.ItemNames;
import com.back.space.application.SpaceAccessPolicy;
import com.back.space.application.SpaceAccessPolicy.Action;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrashServiceImpl implements TrashService {

    private final DriveItemRepository repository;
    private final SpaceAccessPolicy accessPolicy;
    private final DriveItems items;
    private final ItemRemover remover;
    private final FileProperties properties;
    private final Clock clock;

    /**
     * 원래 폴더로 돌린다. 원래 폴더가 없거나 휴지통에 있거나, 돌아가면 10단계를 넘으면 최상위로 돌린다.
     * 이름이 겹치면 번호를 붙인다 (정책 8.5 제4항).
     */
    @Override
    @Transactional
    public FileDto.RestoreRes restore(long userId, long spaceId, long itemId) {
        lockForWrite(userId, spaceId);
        DriveItem item = items.trashed(spaceId, itemId);
        DriveItem target = originalFolder(item);
        Long targetId = DriveItems.idOf(target);
        int depth = DriveItems.depthUnder(target);

        var taken = new HashSet<>(repository.nameKeysIn(spaceId, targetId));
        String name = ItemNames.available(item.getName(), taken);
        int delta = depth - item.getDepth();
        item.restore(targetId, depth, name);
        if (delta != 0) {
            repository.shiftDescendantDepth(itemId, delta);
        }
        repository.flush();
        return new FileDto.RestoreRes(itemId, name, targetId);
    }

    private DriveItem originalFolder(DriveItem item) {
        Long originalId = item.getOriginalParentId();
        if (originalId == null) {
            return null;
        }
        DriveItem original = repository.findByIdAndSpaceId(originalId, item.getSpaceId()).orElse(null);
        if (original == null || !repository.hasNoTrashedAncestor(originalId, item.getSpaceId())) {
            return null;
        }
        int depth = original.getDepth() + 1;
        if (item.isFolder() && depth + repository.folderHeight(item.getId()) > DriveItem.MAX_FOLDER_DEPTH) {
            return null;
        }
        return original;
    }

    @Override
    @Transactional
    public void purge(long userId, long spaceId, long itemId) {
        lockForWrite(userId, spaceId);
        DriveItem item = items.trashed(spaceId, itemId);
        remover.remove(List.of(item.getId()));
    }

    @Override
    @Transactional
    public void empty(long userId, long spaceId) {
        lockForWrite(userId, spaceId);
        remover.remove(repository.findBySpaceIdAndTrashedAtIsNotNull(spaceId).stream().map(DriveItem::getId).toList());
    }

    @Override
    public boolean isEmpty(long spaceId) {
        return !repository.existsBySpaceIdAndTrashedAtIsNotNull(spaceId);
    }

    @Override
    @Transactional
    public int purgeExpired() {
        List<Long> ids = repository.findTrashedBefore(clock.instant().minus(properties.trashRetention())).stream()
                .map(DriveItem::getId)
                .toList();
        remover.remove(ids);
        if (!ids.isEmpty()) {
            log.info("Purged {} expired trash items", ids.size());
        }
        return ids.size();
    }

    private void lockForWrite(long userId, long spaceId) {
        accessPolicy.checkAndLock(userId, spaceId, Action.WRITE);
        items.requireNoHandover(spaceId);
    }
}
