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
}
