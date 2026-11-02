package com.back.group.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /** 멤버 변경·초대·삭제처럼 그룹 단위로 순서가 필요한 쓰기는 그룹 행을 잠그고 시작한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    Optional<Group> findForUpdate(long id);

    boolean existsByOwnerId(long ownerId);
}
