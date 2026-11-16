package com.back.file.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DriveItemRepository extends JpaRepository<DriveItem, Long> {

    Optional<DriveItem> findByIdAndSpaceId(long id, long spaceId);

    /**
     * 휴지통에 있지 않은 항목인지. 자기 자신과 조상이 모두 휴지통 표시가 없어야 한다.
     * 휴지통 항목은 트리에서 떼어 두므로, 조상을 따라 올라가 최상위에 닿았을 때 그 최상위에 표시가 없으면 된다.
     */
    @Query(value = """
            WITH RECURSIVE up AS (
                SELECT id, parent_id, trashed_at FROM drive_item WHERE id = :id AND space_id = :spaceId
                UNION ALL
                SELECT p.id, p.parent_id, p.trashed_at FROM drive_item p JOIN up ON p.id = up.parent_id
            )
            SELECT count(*) = 0 FROM up WHERE trashed_at IS NOT NULL
            """, nativeQuery = true)
    boolean hasNoTrashedAncestor(long id, long spaceId);

    /** 폴더의 조상 id (자기 자신 포함). 이동 대상이 자기 하위인지 판단한다. */
    @Query(value = """
            WITH RECURSIVE up AS (
                SELECT id, parent_id FROM drive_item WHERE id = :id
                UNION ALL
                SELECT p.id, p.parent_id FROM drive_item p JOIN up ON p.id = up.parent_id
            )
            SELECT id FROM up
            """, nativeQuery = true)
    List<Long> ancestorIds(long id);

    /** 항목과 그 하위 전체의 id (휴지통에서 떼어 둔 항목은 포함되지 않는다). */
    @Query(value = """
            WITH RECURSIVE down AS (
                SELECT id FROM drive_item WHERE id = :id
                UNION ALL
                SELECT c.id FROM drive_item c JOIN down ON c.parent_id = down.id
            )
            SELECT id FROM down
            """, nativeQuery = true)
    List<Long> subtreeIds(long id);

    /** 항목 아래 가장 깊은 폴더가 항목보다 몇 단계 아래인지 (항목이 폴더면 0부터). 폴더가 없으면 -1. */
    @Query(value = """
            WITH RECURSIVE down AS (
                SELECT id, type, 0 AS level FROM drive_item WHERE id = :id
                UNION ALL
                SELECT c.id, c.type, down.level + 1 FROM drive_item c JOIN down ON c.parent_id = down.id
            )
            SELECT coalesce(max(level), -1) FROM down WHERE type = 'FOLDER'
            """, nativeQuery = true)
    int folderHeight(long id);

    /** 하위 항목의 깊이를 {@code delta}만큼 바꾼다 (이동·복구). 항목 자신은 엔티티로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            WITH RECURSIVE down AS (
                SELECT id FROM drive_item WHERE parent_id = :id
                UNION ALL
                SELECT c.id FROM drive_item c JOIN down ON c.parent_id = down.id
            )
            UPDATE drive_item SET depth = depth + :delta WHERE id IN (SELECT id FROM down)
            """, nativeQuery = true)
    int shiftDescendantDepth(long id, int delta);

    /** 폴더 안(휴지통 제외)의 이름 키. {@code parentId}가 null이면 최상위. */
    default List<String> nameKeysIn(long spaceId, Long parentId) {
        return parentId == null ? rootNameKeys(spaceId) : childNameKeys(spaceId, parentId);
    }

    /** 폴더 안(휴지통 제외)에서 이름 키가 같은 항목. {@code parentId}가 null이면 최상위. */
    default Optional<DriveItem> findByName(long spaceId, Long parentId, String nameKey) {
        return parentId == null ? findRootByName(spaceId, nameKey) : findChildByName(spaceId, parentId, nameKey);
    }

    @Query("select i.nameKey from DriveItem i where i.spaceId = :spaceId and i.parentId is null and i.trashedAt is null")
    List<String> rootNameKeys(long spaceId);

    @Query("select i.nameKey from DriveItem i where i.spaceId = :spaceId and i.parentId = :parentId")
    List<String> childNameKeys(long spaceId, long parentId);

    @Query("""
            select i from DriveItem i
            where i.spaceId = :spaceId and i.parentId is null and i.trashedAt is null and i.nameKey = :nameKey
            """)
    Optional<DriveItem> findRootByName(long spaceId, String nameKey);

    @Query("""
            select i from DriveItem i
            where i.spaceId = :spaceId and i.parentId = :parentId and i.nameKey = :nameKey
            """)
    Optional<DriveItem> findChildByName(long spaceId, long parentId, String nameKey);

    List<DriveItem> findBySpaceIdAndTrashedAtIsNotNull(long spaceId);

    boolean existsBySpaceIdAndTrashedAtIsNotNull(long spaceId);

    /** 보관 기간이 지난 휴지통 항목 (정책 8.5 제5항). */
    @Query("select i from DriveItem i where i.trashedAt <= :cutoff")
    List<DriveItem> findTrashedBefore(Instant cutoff);

    /** 지울 파일의 저장소 키. */
    @Query("select i from DriveItem i where i.id in :ids and i.type = com.back.file.domain.DriveItem.Type.FILE")
    List<DriveItem> findFilesIn(List<Long> ids);

    /** 한 문장으로 지운다. 자기 참조 FK는 문장이 끝날 때 검사하므로 하위 항목까지 한 번에 지울 수 있다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM drive_item WHERE id IN (:ids)", nativeQuery = true)
    int deleteAllByIds(List<Long> ids);

    @Query(value = "SELECT count(*) > 0 FROM drive_item WHERE storage_key = :storageKey", nativeQuery = true)
    boolean existsByStorageKey(String storageKey);
}
