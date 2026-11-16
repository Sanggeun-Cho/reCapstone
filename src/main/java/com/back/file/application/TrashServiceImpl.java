package com.back.file.application;

import com.back.file.FileProperties;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.file.support.ItemNames;
import com.back.space.domain.SpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TrashServiceImpl implements TrashService {

    private final DriveItemRepository repository;
    private final SpaceRepository spaceRepository;
    private final DriveItems items;
    private final ItemRemover remover;
    private final FileProperties properties;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public TrashServiceImpl(DriveItemRepository repository, SpaceRepository spaceRepository, DriveItems items,
            ItemRemover remover, FileProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.spaceRepository = spaceRepository;
        this.items = items;
        this.remover = remover;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 원래 폴더로 돌린다. 원래 폴더가 없거나 휴지통에 있거나, 돌아가면 10단계를 넘으면 최상위로 돌린다.
     * 이름이 겹치면 번호를 붙인다 (정책 8.5 제4항).
     */
    @Override
    @Transactional
    public FileDto.RestoreRes restore(long userId, long spaceId, long itemId) {
        items.lockForWrite(userId, spaceId);
        DriveItem item = items.trashed(spaceId, itemId);
        DriveItem target = originalFolder(item);
        Long targetId = DriveItems.idOf(target);
        int depth = DriveItems.depthUnder(target);

        var taken = new HashSet<>(repository.nameKeysIn(spaceId, targetId));
        String name = ItemNames.available(item.getName(), taken);
        int delta = depth - item.getDepth();
        item.restore(targetId, depth, name);
        items.flushNames();
        if (delta != 0) {
            repository.shiftDescendantDepth(itemId, delta);
        }
        return new FileDto.RestoreRes(itemId, name, targetId);
    }

    private DriveItem originalFolder(DriveItem item) {
        Long originalId = item.getOriginalParentId();
        if (originalId == null) {
            return null;
        }
        DriveItem original = repository.findByIdAndSpaceId(originalId, item.getSpaceId()).orElse(null);
        if (original == null || !repository.isActive(originalId, item.getSpaceId())) {
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
        items.lockForWrite(userId, spaceId);
        DriveItem item = items.trashed(spaceId, itemId);
        remover.remove(List.of(item.getId()));
    }

    @Override
    @Transactional
    public void empty(long userId, long spaceId) {
        items.lockForWrite(userId, spaceId);
        remover.remove(repository.findBySpaceIdAndTrashedAtIsNotNull(spaceId).stream().map(DriveItem::getId).toList());
    }

    @Override
    public boolean isEmpty(long spaceId) {
        return !repository.existsBySpaceIdAndTrashedAtIsNotNull(spaceId);
    }

    /**
     * 스페이스마다 따로 트랜잭션을 열고 스페이스 행을 잠근 뒤 대상을 다시 읽는다. 잠그기 전에 고른 항목을 지우면,
     * 그 사이 담당자가 복구한 항목까지 지울 수 있기 때문이다. 복구도 같은 스페이스 행을 잠근다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(properties.trashRetention());
        int purged = 0;
        for (long spaceId : repository.findSpaceIdsWithTrashBefore(cutoff)) {
            Integer count = newTransaction.execute(status -> {
                spaceRepository.lockRow(spaceId);
                List<Long> ids = repository.findTrashedBefore(spaceId, cutoff);
                remover.remove(ids);
                return ids.size();
            });
            purged += count == null ? 0 : count;
        }
        if (purged > 0) {
            log.info("Purged {} expired trash items", purged);
        }
        return purged;
    }
}
