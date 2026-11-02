package com.back.space.query;

import com.back.space.domain.AssignmentChange;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 스페이스 화면 조회 (MyBatis, SELECT 전용). SQL은 같은 이름의 XML에 있다.
 */
@Mapper
public interface SpaceQueryMapper {

    long countGroupSpaces(@Param("groupId") long groupId);

    /** 그룹의 삭제되지 않은 스페이스 한 페이지. 담당자 이름과 대기 중인 삭제 요청 여부를 함께 읽는다. */
    List<SpaceRow> findGroupSpaces(@Param("groupId") long groupId, @Param("offset") long offset,
            @Param("limit") int limit, @Param("now") Instant now);

    List<AssignmentChangeRow> findAssignmentChanges(@Param("spaceId") long spaceId,
            @Param("withdrawnName") String withdrawnName);

    record SpaceRow(long id, String name, Long assigneeId, String assigneeName, boolean deletionPending) {
    }

    record AssignmentChangeRow(long id, AssignmentChange.Type type, Long fromUserId, String fromUserName,
            Long toUserId, String toUserName, long actorId, String actorName, String reason, Instant occurredAt) {
    }
}
