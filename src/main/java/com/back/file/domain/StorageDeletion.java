package com.back.file.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저장소 객체 삭제 대기열 (작업 대기열, AGENTS.md 엔티티 예외). 처리가 끝나면 행을 지운다.
 * DB 행 삭제와 같은 트랜잭션에서 기록하므로, 롤백되면 대기열도 남지 않고 커밋되면 객체 삭제를 잊지 않는다.
 */
@Getter
@Entity
@Table(name = "storage_deletion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageDeletion {

    private static final int ERROR_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storageKey;

    private String keyPrefix;

    @Column(nullable = false)
    private Instant createdAt;

    private int attempts;

    private String lastError;

    public static StorageDeletion ofKey(String storageKey, Instant at) {
        StorageDeletion deletion = new StorageDeletion();
        deletion.storageKey = storageKey;
        deletion.createdAt = at;
        return deletion;
    }

    public static StorageDeletion ofPrefix(String keyPrefix, Instant at) {
        StorageDeletion deletion = new StorageDeletion();
        deletion.keyPrefix = keyPrefix;
        deletion.createdAt = at;
        return deletion;
    }

    public void failed(String error) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), ERROR_MAX));
    }
}
