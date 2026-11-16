package com.back.file.application;

import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.file.event.FileRemoved;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴지통 항목을 영구 삭제한다 (정책 8.5 제3항, 제5항). 하위 항목까지 DB 행을 지우고, 파일 객체는 삭제 대기열에 넣는다.
 */
@Component
@RequiredArgsConstructor
class ItemRemover {

    private final DriveItemRepository repository;
    private final StorageDeletionService storageDeletion;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    void remove(Collection<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        rootIds.forEach(id -> ids.addAll(repository.subtreeIds(id)));
        List<DriveItem> files = repository.findFilesIn(List.copyOf(ids));
        List<String> keys = files.stream().map(DriveItem::getStorageKey).toList();
        List<FileRemoved> removed = files.stream().map(f -> new FileRemoved(f.getSpaceId(), f.getId())).toList();

        repository.deleteAllByIds(List.copyOf(ids));
        storageDeletion.enqueueKeys(keys);
        removed.forEach(events::publishEvent);
    }
}
