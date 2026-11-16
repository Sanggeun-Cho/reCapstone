package com.back.space.domain;

import com.back.common.entity.BaseEntity;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * 스페이스 삭제 요청 (정책 3.4). OWNER와 담당자 중 한 사람이 요청하고 다른 사람이 승인한다.
 */
@Getter
@Entity
@Table(name = "space_deletion_request")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceDeletionRequest extends BaseEntity {

    public enum Status {
        PENDING, APPROVED, REJECTED, WITHDRAWN, INVALIDATED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long spaceId;

    @Column(nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant expiresAt;

    private Long decidedBy;

    private Instant decidedAt;

    private String reason;

    private SpaceDeletionRequest(long spaceId, long requesterId, Status status, Instant expiresAt) {
        this.spaceId = spaceId;
        this.requesterId = requesterId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static SpaceDeletionRequest pending(long spaceId, long requesterId, Instant expiresAt) {
        return new SpaceDeletionRequest(spaceId, requesterId, Status.PENDING, expiresAt);
    }

    /** 동의할 상대가 없어 요청과 동시에 승인된 요청 (정책 3.4 제2항). */
    public static SpaceDeletionRequest approvedAlone(long spaceId, long requesterId, Instant at) {
        SpaceDeletionRequest request = new SpaceDeletionRequest(spaceId, requesterId, Status.APPROVED, at);
        request.decidedBy = requesterId;
        request.decidedAt = at;
        return request;
    }

    /** 대기 중이고 기한이 남았는지. 기한과 같은 시각이면 만료로 본다. */
    public boolean isPending(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    public boolean isRequestedBy(long userId) {
        return requesterId == userId;
    }

    public void approve(long userId, Instant at) {
        decide(Status.APPROVED, userId, at);
    }

    public void reject(long userId, String reason, Instant at) {
        decide(Status.REJECTED, userId, at);
        this.reason = reason;
    }

    public void withdraw(Instant at) {
        decide(Status.WITHDRAWN, requesterId, at);
    }

    /** 기한이 지난 대기 요청을 닫아 새 요청이 들어올 자리(부분 유니크)를 비운다. */
    public void expire() {
        this.status = Status.EXPIRED;
    }

    private void decide(Status status, long userId, Instant at) {
        this.status = status;
        this.decidedBy = userId;
        this.decidedAt = at;
    }
}
