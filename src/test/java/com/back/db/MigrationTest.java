package com.back.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("NFR-06: 빈 DB에 Flyway를 적용한 뒤 애플리케이션이 기동한다")
    void migrationsApplyOnEmptyDatabase() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        Integer v1 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);

        assertThat(failed).isZero();
        assertThat(v1).isOne();
    }

    @Test
    @DisplayName("V1: vector 확장이 설치된다")
    void vectorExtensionInstalled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

        assertThat(count).isOne();
    }
}
