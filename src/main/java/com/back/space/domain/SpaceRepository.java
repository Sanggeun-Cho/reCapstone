package com.back.space.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SpaceRepository extends JpaRepository<Space, Long> {

    /** 삭제되지 않은 스페이스의 그룹 id. 엔티티를 읽지 않으므로 잠그기 전에 써도 낡은 상태가 남지 않는다. */
    @Query("select s.groupId from Space s where s.id = :id")
    Optional<Long> findGroupId(long id);

    /** 담당자 변경과 삭제 요청은 스페이스 행을 잠그고 순서대로 처리한다. 그룹 행을 먼저 잠근 뒤에 부른다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findForUpdate(long id);

    /** 삭제되지 않은 그룹의 삭제되지 않은 스페이스 중 그 사용자가 담당하는 곳이 그룹에 있는지. */
    @Query("""
            select count(s) > 0 from Space s, com.back.group.domain.Group g
            where g.id = s.groupId and s.groupId = :groupId and s.assigneeId = :userId
            """)
    boolean existsActiveAssignment(long groupId, long userId);

    /** 보관 중이 아닌 곳에서 담당 중인 스페이스가 있는지 (탈퇴 차단, 정책 7.8). */
    @Query("""
            select count(s) > 0 from Space s, com.back.group.domain.Group g
            where g.id = s.groupId and s.assigneeId = :userId
            """)
    boolean existsActiveAssignment(long userId);

    /**
     * 삭제되어 보관 중인 스페이스에서 그 사용자의 담당을 지운다. 그룹을 떠나는 멤버는 복구되는 스페이스에 다시 포함되지 않는다
     * (정책 2.6 제4항). 담당자 복합 FK가 멤버 행 삭제를 막지 않게 하려는 처리이며 담당자 변경 이력에는 남기지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE space SET assignee_id = NULL
            WHERE group_id = :groupId AND assignee_id = :userId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int clearArchivedAssignments(long groupId, long userId);

    /**
     * 그룹의 모든 스페이스에서 그 사용자의 담당을 지운다. 보관 중인 그룹에서는 스페이스 행이 삭제 표시 없이 남아 있으므로
     * {@link #clearArchivedAssignments}로는 비워지지 않는다. 담당자 변경 이력에는 남기지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE space SET assignee_id = NULL WHERE group_id = :groupId AND assignee_id = :userId",
            nativeQuery = true)
    int clearAllAssignmentsInGroup(long groupId, long userId);
}
