package com.back.group.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Id> {

    default boolean isMember(long groupId, long userId) {
        return existsById(new GroupMember.Id(groupId, userId));
    }

    /** 삭제되지 않은 그룹 중 그 사용자가 멤버인 그룹 id. 보관 중인 그룹은 빼고 본다 (정책 2.6 제4항). */
    @Query("""
            select m.id.groupId from GroupMember m, Group g
            where g.id = m.id.groupId and m.id.userId = :userId
            """)
    List<Long> findActiveGroupIds(long userId);

    /**
     * 삭제되어 보관 중인 그룹 중 그 사용자가 OWNER가 아닌 멤버인 그룹 id. 그룹 행을 잠근다.
     * 보관 중인 그룹의 OWNER가 탈퇴한 경우의 처리는 아직 정하지 않아 제외한다.
     */
    @Query(value = """
            SELECT g.id FROM groups g
            JOIN group_member m ON m.group_id = g.id AND m.user_id = :userId
            WHERE g.deleted_at IS NOT NULL AND g.owner_id <> :userId
            ORDER BY g.id
            FOR UPDATE OF g
            """, nativeQuery = true)
    List<Long> lockArchivedGroupIdsAsNonOwner(long userId);
}
