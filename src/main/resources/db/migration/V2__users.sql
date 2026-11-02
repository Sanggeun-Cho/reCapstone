-- S1 회원·인증 (정책 제7장)

CREATE TABLE users
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 소문자로 정규화해 저장한다
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    work_phone        VARCHAR(20),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    email_verified_at TIMESTAMPTZ,
    -- 2단계 인증: 비밀키는 AES-GCM 암호문. 등록을 시작하면 채워지고, 확인을 마치면 totp_enabled_at이 기록된다
    totp_secret       VARCHAR(255),
    totp_enabled_at   TIMESTAMPTZ,
    -- 마지막으로 쓴 TOTP 시간 구간. 같은 코드의 재사용을 막는다
    totp_last_step    BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    deleted_at        TIMESTAMPTZ
);

-- 탈퇴(soft delete)한 이메일로 다시 가입할 수 있다 (정책 7.8)
CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE deleted_at IS NULL;

CREATE TABLE recovery_code
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    -- SHA-256 16진수. 원문은 보관하지 않는다 (정책 7.3)
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_recovery_code_user ON recovery_code (user_id) WHERE deleted_at IS NULL;
