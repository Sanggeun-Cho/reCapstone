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
 * <p>
 * 코드 규약과 달리 감사 필드와 {@code deleted_at}을 두지 않고 {@code BaseEntity}도 상속하지 않는다.
 * 행을 바꿀 수 없으니 수정자·수정 시각·삭제 시각은 생길 수 없고, 누가 언제 남겼는지는 {@code actorId}·{@code occurredAt}이 담는다.
 * {@code BaseEntity}를 상속하면 막힌 경로인 {@code softDelete()}가 열리게 된다. 지우는 것은 영구 삭제 작업뿐이다.
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
