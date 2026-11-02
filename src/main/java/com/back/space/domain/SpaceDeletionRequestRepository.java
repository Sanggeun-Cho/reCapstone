package com.back.space.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SpaceDeletionRequestRepository extends JpaRepository<SpaceDeletionRequest, Long> {

    Optional<SpaceDeletionRequest> findBySpaceIdAndStatus(long spaceId, SpaceDeletionRequest.Status status);

    /** 담당자가 바뀌면 그 스페이스의 대기 요청이 무효가 된다 (정책 3.4 제6항). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update SpaceDeletionRequest r
            set r.status = com.back.space.domain.SpaceDeletionRequest.Status.INVALIDATED, r.decidedAt = :at
            where r.spaceId = :spaceId and r.status = com.back.space.domain.SpaceDeletionRequest.Status.PENDING
            """)
    int invalidatePendingOfSpace(long spaceId, Instant at);

    /** OWNER가 바뀌거나 그룹이 삭제되면 그 그룹 모든 스페이스의 대기 요청이 무효가 된다 (정책 2.5, 3.4 제6항). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE space_deletion_request SET status = 'INVALIDATED', decided_at = :at, updated_at = :at
            WHERE status = 'PENDING' AND space_id IN (SELECT id FROM space WHERE group_id = :groupId)
            """, nativeQuery = true)
    int invalidatePendingOfGroup(long groupId, Instant at);
}
