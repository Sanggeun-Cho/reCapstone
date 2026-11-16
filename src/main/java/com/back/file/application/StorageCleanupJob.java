package com.back.file.application;

import com.back.file.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 휴지통 정리(30일), 삭제 대기열 재처리, 고아 객체 정리를 한다. 실행 시각은 {@code app.storage.cleanup-cron}으로 바꾸고,
 * {@code -}로 끈다. 그룹·스페이스 영구 삭제(S2)가 대기열에 넣은 스페이스 객체도 여기서 다시 지운다.
 */
@Component
@RequiredArgsConstructor
public class StorageCleanupJob {

    private final TrashService trashService;
    private final StorageDeletionService storageDeletionService;
    private final StorageCleanupService cleanupService;
    private final FileProperties properties;

    @Scheduled(cron = "${app.storage.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        trashService.purgeExpired();
        storageDeletionService.retryPending();
        cleanupService.deleteOrphans(properties.orphanGrace());
    }
}
