package com.back.space.domain;

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
 * 담당자 변경 이력. 추가 전용이며 DB 트리거가 UPDATE·DELETE를 거부한다 (정책 3.2 제3항).
 * 행이 바뀌지 않으므로 감사 필드와 {@code deleted_at}을 두지 않는다.
 */
@Getter
@Entity
@Immutable
@Table(name = "assignment_change")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentChange {

    public enum Type {
        INITIAL, HANDOVER, FORCED, REFILL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long spaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private Long fromUserId;

    private Long toUserId;

    @Column(nullable = false)
    private Long actorId;

    private String reason;

    @Column(nullable = false)
    private Instant occurredAt;

    AssignmentChange(long spaceId, Type type, Long fromUserId, Long toUserId, long actorId, String reason,
            Instant occurredAt) {
        this.spaceId = spaceId;
        this.type = type;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
