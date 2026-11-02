package com.back.group.query;

import com.back.group.domain.GroupMemberHistory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 그룹 화면 조회 (MyBatis, SELECT 전용). SQL은 같은 이름의 XML에 있다.
 * 탈퇴한 사용자의 이름은 {@code withdrawnName}으로 바꿔 돌려준다.
 */
@Mapper
public interface GroupQueryMapper {

    /** 내가 멤버인 삭제되지 않은 그룹. id 내림차순, {@code cursor}보다 작은 id부터 {@code limit}건. */
    List<GroupRow> findMyGroups(@Param("userId") long userId, @Param("cursor") Long cursor,
            @Param("limit") int limit);

    Optional<DetailRow> findDetail(@Param("groupId") long groupId);

    List<MemberRow> findMembers(@Param("groupId") long groupId);

    List<HistoryRow> findHistory(@Param("groupId") long groupId, @Param("withdrawnName") String withdrawnName);

    List<InvitationRow> findPendingInvitations(@Param("groupId") long groupId, @Param("now") Instant now);

    List<ReceivedInvitationRow> findReceivedInvitations(@Param("email") String email, @Param("now") Instant now);

    record GroupRow(long id, String name, boolean owner) {
    }

    record DetailRow(long id, String name, long ownerId, String ownerName, long memberCount) {
    }

    record MemberRow(long userId, String name, String email, String workPhone, boolean owner, Instant joinedAt) {
    }

    record HistoryRow(long userId, String userName, GroupMemberHistory.Type type, long actorId, String actorName,
            Instant occurredAt) {
    }

    record InvitationRow(long id, String email, Instant expiresAt) {
    }

    record ReceivedInvitationRow(long id, long groupId, String groupName, String inviterName, Instant expiresAt) {
    }
}
