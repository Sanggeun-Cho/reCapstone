package com.back.auth.domain;

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
 * 2단계 인증 복구 코드 (정책 7.3). 원문은 발급할 때 한 번만 보여 주고, 해시만 저장한다.
 */
@Getter
@Entity
@Table(name = "recovery_code")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecoveryCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String codeHash;

    private Instant usedAt;

    public RecoveryCode(long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public void use(Instant at) {
        this.usedAt = at;
    }

    /** 다시 발급하거나 2단계 인증을 끄면 이전 코드를 무효로 한다. */
    public void discard(Instant at) {
        softDelete(at);
    }
}
