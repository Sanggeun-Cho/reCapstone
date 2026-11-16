package com.back.file.application;

import com.back.file.domain.DriveItemRepository;
import com.back.file.domain.StorageDeletionRepository;
import com.back.file.storage.FileStorage;
import com.back.file.storage.StorageKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고아 객체 정리 (DESIGN_REVIEW.md 6.8). {@code spaces/} 아래 객체 중 유예 시간이 지났는데 DB에도 삭제 대기열에도 없는 객체를 지운다.
 * 업로드 직후 기록 단계 전이거나 기록에 실패해 지우지 못한 객체가 여기에 해당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 저장소 목록을 훑는 동안 DB 커넥션을 쥐지 않도록 트랜잭션을 열지 않는다. DB 조회는 저장소 객체마다 짧게 한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StorageCleanupServiceImpl implements StorageCleanupService {


    private final FileStorage storage;
    private final DriveItemRepository itemRepository;
    private final StorageDeletionRepository deletionRepository;

    /**
     * 유예 시간은 저장소가 기록한 수정 시각과 실제 현재 시각으로 잰다. 테스트에서 움직이는 애플리케이션 시계와는 무관하다.
     *
     * @return 지운 객체 수
     */
    @Override
    public int deleteOrphans(Duration grace) {
        Instant cutoff = Clock.systemUTC().instant().minus(grace);
        List<String> orphans = new ArrayList<>();
        storage.list(StorageKeys.ROOT, object -> {
            if (object.lastModified().isBefore(cutoff)
                    && !itemRepository.existsByStorageKey(object.key())
                    && !deletionRepository.covers(object.key())) {
                orphans.add(object.key());
            }
        });
        orphans.forEach(storage::delete);
        if (!orphans.isEmpty()) {
            log.info("Deleted {} orphan objects", orphans.size());
        }
        return orphans.size();
    }
}
