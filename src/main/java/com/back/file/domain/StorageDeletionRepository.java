package com.back.file.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StorageDeletionRepository extends JpaRepository<StorageDeletion, Long> {

    @Query("select d.id from StorageDeletion d order by d.id")
    List<Long> findAllIds();

    @Query(value = """
            SELECT count(*) > 0 FROM storage_deletion
            WHERE storage_key = :key OR (key_prefix IS NOT NULL AND :key LIKE key_prefix || '%')
            """, nativeQuery = true)
    boolean covers(String key);
}
