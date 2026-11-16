package com.back.group.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByGroupIdAndEmailAndStatus(long groupId, String email, Invitation.Status status);

    /** 그룹을 삭제하면 대기 중인 초대는 무효가 된다 (정책 2.5 제4항). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Invitation i set i.status = com.back.group.domain.Invitation.Status.CANCELED, i.respondedAt = :at
            where i.groupId = :groupId and i.status = com.back.group.domain.Invitation.Status.PENDING
            """)
    int cancelPendingOfGroup(long groupId, Instant at);
}
