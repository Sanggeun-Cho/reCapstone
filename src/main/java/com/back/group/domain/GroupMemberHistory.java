package com.back.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 그룹 멤버 이력. 추가 전용이며 DB 트리거가 UPDATE·DELETE를 거부한다 (정책 2.3 제4항).
 * 행이 바뀌지 않으므로 감사 필드와 {@code deleted_at}을 두지 않는다.
 */
@Getter
@Entity
@Immutable
@Table(name = "group_member_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMemberHistory {

    public enum Type {
        JOINED, LEFT, REMOVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false)
    private Instant occurredAt;

    public GroupMemberHistory(long groupId, long userId, Type type, long actorId, Instant occurredAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.type = type;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }
}
