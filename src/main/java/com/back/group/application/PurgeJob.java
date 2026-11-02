package com.back.group.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 영구 삭제를 실행한다. 실행 시각은 {@code app.group.purge-cron}으로 바꾸고, {@code -}로 끈다.
 */
@Component
@RequiredArgsConstructor
public class PurgeJob {

    private final PurgeService purgeService;

    @Scheduled(cron = "${app.group.purge-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        purgeService.purgeExpired();
    }
}
