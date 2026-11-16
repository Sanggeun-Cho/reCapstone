package com.back.group.domain;

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
 * 그룹 초대 (정책 2.4). 만료는 상태를 바꾸지 않고 {@code expires_at}으로 판단한다.
 */
@Getter
@Entity
@Table(name = "invitation")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseEntity {

    public enum Status {
        PENDING, ACCEPTED, DECLINED, CANCELED, REPLACED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Long inviterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant respondedAt;

    public Invitation(long groupId, String email, long inviterId, Instant expiresAt) {
        this.groupId = groupId;
        this.email = email;
        this.inviterId = inviterId;
        this.status = Status.PENDING;
        this.expiresAt = expiresAt;
    }

    /** 대기 중이고 기한이 남았는지. 기한과 같은 시각이면 만료로 본다. */
    public boolean isPending(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    public void accept(Instant at) {
        respond(Status.ACCEPTED, at);
    }

    public void decline(Instant at) {
        respond(Status.DECLINED, at);
    }

    public void cancel(Instant at) {
        respond(Status.CANCELED, at);
    }

    /** 같은 이메일을 다시 초대해 이 초대를 대체한다. 만료된 대기 초대도 대체해 유니크 제약을 비운다. */
    public void replace(Instant at) {
        respond(Status.REPLACED, at);
    }

    private void respond(Status status, Instant at) {
        this.status = status;
        this.respondedAt = at;
    }
}
