package com.back.space.domain;

import com.back.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 스페이스. 담당자는 없거나 1명이며 그룹 멤버여야 한다 (정책 3.2, DB 복합 FK).
 * 담당자 변경은 {@link AssignmentChange}로 남긴다.
 */
@Getter
@Entity
@Table(name = "space")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space extends BaseEntity {

    public static final int NAME_MAX = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private String name;

    private Long assigneeId;

    public Space(long groupId, String name) {
        this.groupId = groupId;
        this.name = name;
    }

    public boolean hasAssignee() {
        return assigneeId != null;
    }

    public boolean isAssignee(long userId) {
        return assigneeId != null && assigneeId == userId;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** 담당자를 바꾸고 변경 이력을 만든다. 이력 저장은 호출한 쪽이 한다. */
    public AssignmentChange changeAssignee(AssignmentChange.Type type, Long to, long actorId, String reason, Instant at) {
        AssignmentChange change = new AssignmentChange(id, type, assigneeId, to, actorId, reason, at);
        this.assigneeId = to;
        return change;
    }

    public void delete(Instant at) {
        softDelete(at);
    }
}
