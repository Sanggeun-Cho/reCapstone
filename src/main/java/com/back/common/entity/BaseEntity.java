package com.back.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 감사 필드와 soft delete 컬럼을 가진 엔티티의 공통 상위 클래스.
 * <p>
 * 하위 엔티티에는 {@code @SQLRestriction(BaseEntity.NOT_DELETED)}를 붙여 삭제된 행을 조회에서 제외한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    public static final String NOT_DELETED = "deleted_at IS NULL";

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;

    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    protected void softDelete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }
}
