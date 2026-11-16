package com.back.space.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentChangeRepository extends JpaRepository<AssignmentChange, Long> {

    boolean existsBySpaceIdAndType(long spaceId, AssignmentChange.Type type);
}
