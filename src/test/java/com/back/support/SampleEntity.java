package com.back.support;

import com.back.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * {@link BaseEntity} 검증용 테스트 엔티티. 테이블은 {@code db/test-migration}에 있다.
 */
@Getter
@Entity
@Table(name = "test_sample")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SampleEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public SampleEntity(String name) {
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void delete() {
        softDelete();
    }
}
