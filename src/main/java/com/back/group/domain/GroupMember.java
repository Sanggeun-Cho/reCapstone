package com.back.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹의 현재 멤버. soft delete하지 않고 행을 지운다. 가입·이탈 기록은 {@link GroupMemberHistory}에 남는다 (정책 2.3 제4항).
 */
@Getter
@Entity
@Table(name = "group_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @EmbeddedId
    private Id id;

    @Column(nullable = false)
    private Instant joinedAt;

    public GroupMember(long groupId, long userId, Instant joinedAt) {
        this.id = new Id(groupId, userId);
        this.joinedAt = joinedAt;
    }

    @Embeddable
    public record Id(@Column(name = "group_id") long groupId, @Column(name = "user_id") long userId)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
