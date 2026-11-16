package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.file.application.StorageCleanupService;
import com.back.support.FileTestSupport;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 저장소 정리: 삭제 대기열과 고아 객체 (DESIGN_REVIEW.md 6.8). */
class StorageCleanupJobTest extends FileTestSupport {

    @Autowired
    StorageCleanupService cleanupService;

    private long queued() {
        return count("SELECT count(*) FROM storage_deletion");
    }

    @Test
    @DisplayName("STOR-01: 객체 삭제가 실패해도 영구 삭제는 204, DB 행은 지워지고 대기열에 1행(attempts 1)")
    void deletionFailureQueued() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        String key = storageKey(f);
        trash(f);
        fileStorage.failDeletes();

        delete(trashUrl(s) + "/" + f, BOB).andExpect(status().isNoContent());
        assertThat(itemRepository.findById(f)).isEmpty();
        assertThat(queued()).isEqualTo(1);
        assertThat(count("SELECT attempts FROM storage_deletion")).isEqualTo(1);
        assertThat(objectExists(key)).isTrue();

        // STOR-01b: 장애가 풀린 뒤 정리 작업이 다시 지운다
        fileStorage.reset();
        assertThat(storageDeletionService.retryPending()).isEqualTo(1);
        assertThat(objectExists(key)).isFalse();
        assertThat(queued()).isZero();
    }

    @Test
    @DisplayName("STOR-01c: 영구 삭제 트랜잭션이 롤백되면 DB 행·객체가 그대로이고 대기열도 남지 않는다")
    void rollbackKeepsEverything() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        String key = storageKey(f);
        trash(f);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            as(bob, () -> {
                trashService.purge(bob, s, f);
                return null;
            });
            throw new IllegalStateException("rollback (test)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(itemRepository.findById(f)).isPresent();
        assertThat(objectExists(key)).isTrue();
        assertThat(queued()).isZero();
    }

    @Test
    @DisplayName("STOR-02: 고아 객체 정리는 유예 시간이 지난 DB 밖 객체만 지운다 (DB·휴지통·최근 객체는 남김)")
    void orphans() throws Exception {
        long o3 = uploadOne(null, "o3.txt", "3");
        long o4 = uploadOne(null, "o4.txt", "4");
        trash(o4);
        String o1 = "spaces/" + s + "/files/orphan-old";
        put(o1);
        Instant afterOld = Instant.now();
        while (Instant.now().isBefore(afterOld.plusMillis(3500))) {
            Thread.sleep(200);
        }
        String o2 = "spaces/" + s + "/files/orphan-new";
        put(o2);

        cleanupService.deleteOrphans(Duration.ofSeconds(2));

        assertThat(objectExists(o1)).isFalse();
        assertThat(objectExists(o2)).isTrue();
        assertThat(objectExists(storageKey(o3))).isTrue();
        assertThat(objectExists(storageKey(o4))).isTrue();
    }

    @Test
    @DisplayName("TRASH-13: 스페이스가 영구 삭제되면(S2 작업) 그 스페이스의 객체가 모두 지워지고 대기열도 비어 있다")
    void spacePurgeDeletesObjects() throws Exception {
        long d = folder(null, "D");
        uploadOne(d, "a.txt", "a");
        trash(uploadOne(null, "b.txt", "b"));
        assertThat(objectCount(s)).isEqualTo(2);
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("UPDATE space SET deleted_at = ? WHERE id = ?",
                        java.sql.Timestamp.from(clock.instant()), s));
        clock.advance(Duration.ofDays(31));

        purgeService.purgeExpired();

        assertThat(objectCount(s)).isZero();
        assertThat(queued()).isZero();
        assertThat(count("SELECT count(*) FROM drive_item WHERE space_id = ?", s)).isZero();
        assertThat(count("SELECT count(*) FROM space WHERE id = ?", s)).isZero();
    }

    private void put(String key) {
        byte[] content = "orphan".getBytes();
        fileStorage.real().put(key, new ByteArrayInputStream(content), content.length, "text/plain");
        assertThat(List.of(objectExists(key))).containsExactly(true);
    }
}
