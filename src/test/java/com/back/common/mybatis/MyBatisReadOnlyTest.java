package com.back.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.support.IntegrationTest;
import com.back.support.query.ProbeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MyBatisReadOnlyTest extends IntegrationTest {

    @Autowired
    ProbeMapper probeMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM test_sample");
    }

    @Test
    @DisplayName("SELECT 매핑은 실행되고 snake_case 컬럼을 읽는다")
    void selectAllowed() {
        long id = insertSample("probe");

        assertThat(probeMapper.findName(id)).isEqualTo("probe");
    }

    @Test
    @DisplayName("UPDATE 매핑은 실행 전에 거부된다")
    void writeRejected() {
        long id = insertSample("probe");

        assertThatThrownBy(() -> probeMapper.rename(id, "changed"))
                .rootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("read-only");
        assertThat(probeMapper.findName(id)).isEqualTo("probe");
    }

    private long insertSample(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO test_sample (name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
                Long.class, name);
    }
}
