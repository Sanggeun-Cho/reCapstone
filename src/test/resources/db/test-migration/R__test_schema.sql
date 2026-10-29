-- 테스트 전용 테이블. BaseEntity 검증에 쓴다.
CREATE TABLE IF NOT EXISTS test_sample
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ
);
