package com.back.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 추가 전용 테이블용 공통 트리거 함수 검증. GRP-14, SPC-18의 기반이며 실제 테이블 적용은 S2에서 한다.
 */
class AppendOnlyTriggerTest extends IntegrationTest {

    private static final String TABLE = "test_append_only";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE " + TABLE + " (id BIGINT PRIMARY KEY, note TEXT)");
        jdbcTemplate.execute("CREATE TRIGGER " + TABLE + "_no_modify BEFORE UPDATE OR DELETE ON " + TABLE
                + " FOR EACH ROW EXECUTE FUNCTION reject_append_only_modification()");
        jdbcTemplate.execute("CREATE TRIGGER " + TABLE + "_no_truncate BEFORE TRUNCATE ON " + TABLE
                + " FOR EACH STATEMENT EXECUTE FUNCTION reject_append_only_modification()");
        jdbcTemplate.update("INSERT INTO " + TABLE + " VALUES (1, 'joined')");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE " + TABLE);
    }

    @Test
    @DisplayName("INSERT는 허용한다")
    void insertAllowed() {
        jdbcTemplate.update("INSERT INTO " + TABLE + " VALUES (2, 'left')");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("UPDATE는 트리거가 거부한다")
    void updateRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE " + TABLE + " SET note = 'forged' WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbcTemplate.queryForObject("SELECT note FROM " + TABLE + " WHERE id = 1", String.class))
                .isEqualTo("joined");
    }

    @Test
    @DisplayName("DELETE는 트리거가 거부한다")
    void deleteRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("TRUNCATE는 트리거가 거부한다")
    void truncateRejected() {
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE " + TABLE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }
}
