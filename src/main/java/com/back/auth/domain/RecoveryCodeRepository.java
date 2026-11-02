package com.back.auth.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUserId(long userId);

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(long userId);
}
