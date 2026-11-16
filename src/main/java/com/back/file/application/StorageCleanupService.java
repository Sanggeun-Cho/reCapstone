package com.back.file.application;

import java.time.Duration;

/** 저장소 정리: DB에도 삭제 대기열에도 없는 객체(고아 객체)를 지운다 (DESIGN_REVIEW.md 6.8). */
public interface StorageCleanupService {

    /**
     * 수정된 지 {@code grace}가 지난 고아 객체를 지운다.
     *
     * @return 지운 객체 수
     */
    int deleteOrphans(Duration grace);
}
