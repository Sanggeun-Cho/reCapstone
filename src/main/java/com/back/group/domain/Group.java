package com.back.group.domain;

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
 * 그룹. OWNER는 그룹당 1명이며 항상 멤버다 (정책 2.1, DB 복합 FK). 생성자는 감사 필드 {@code created_by}에 남는다 (정책 2.2).
 */
@Getter
@Entity
@Table(name = "groups")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {

    public static final int NAME_MAX = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long ownerId;

    public Group(String name, long ownerId) {
        this.name = name;
        this.ownerId = ownerId;
    }

    public boolean isOwner(long userId) {
        return ownerId == userId;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void transferOwner(long newOwnerId) {
        this.ownerId = newOwnerId;
    }

    public void delete(Instant at) {
        softDelete(at);
    }
}
