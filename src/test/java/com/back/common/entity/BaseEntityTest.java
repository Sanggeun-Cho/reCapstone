package com.back.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.support.IntegrationTest;
import com.back.support.SampleEntity;
import com.back.support.SampleRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BaseEntityTest extends IntegrationTest {

    @Autowired
    SampleRepository sampleRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.execute("DELETE FROM test_sample");
    }

    @Test
    @DisplayName("저장하면 생성·수정 시각과 요청자 id가 기록된다")
    void auditFieldsFilled() {
        authenticateAs(42L);

        SampleEntity saved = sampleRepository.saveAndFlush(new SampleEntity("first"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(42L);
        assertThat(saved.getUpdatedBy()).isEqualTo(42L);
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("수정하면 수정자만 바뀌고 생성자는 유지된다")
    void updaterChangesOnUpdate() {
        authenticateAs(1L);
        SampleEntity saved = sampleRepository.saveAndFlush(new SampleEntity("first"));

        authenticateAs(2L);
        SampleEntity found = sampleRepository.findById(saved.getId()).orElseThrow();
        found.rename("second");
        SampleEntity updated = sampleRepository.saveAndFlush(found);

        assertThat(updated.getCreatedBy()).isEqualTo(1L);
        assertThat(updated.getUpdatedBy()).isEqualTo(2L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 요청자 id는 비어 있다")
    void noAuditorWithoutAuthentication() {
        SampleEntity saved = sampleRepository.saveAndFlush(new SampleEntity("system"));

        assertThat(saved.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("soft delete한 행은 조회에서 제외되고 DB에는 남는다")
    void softDeletedRowsHidden() {
        SampleEntity kept = sampleRepository.saveAndFlush(new SampleEntity("kept"));
        SampleEntity deleted = sampleRepository.saveAndFlush(new SampleEntity("deleted"));

        deleted.delete();
        sampleRepository.saveAndFlush(deleted);

        assertThat(sampleRepository.findById(deleted.getId())).isEmpty();
        assertThat(sampleRepository.findAll()).extracting(SampleEntity::getId).containsExactly(kept.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM test_sample WHERE id = ?", Boolean.class, deleted.getId()))
                .isTrue();
    }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
