-- S2 그룹·스페이스·권한 (정책 제2장~제4장)

-- 추가 전용 테이블은 영구 삭제(정책 2.6) 작업에서만 지울 수 있다.
-- 그 트랜잭션은 SET LOCAL app.purge = 'on'으로 표시한다. UPDATE는 언제나 거부한다.
CREATE OR REPLACE FUNCTION reject_append_only_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') AND current_setting('app.purge', true) = 'on' THEN
        IF TG_LEVEL = 'ROW' THEN
            RETURN OLD;
        END IF;
        RETURN NULL;
    END IF;
    RAISE EXCEPTION '% is not allowed on append-only table %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TABLE groups
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    owner_id   BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ
);

-- 현재 멤버만 둔다. 떠나거나 제거되면 행을 지우고 group_member_history에 남긴다 (soft delete 예외)
CREATE TABLE group_member
(
    group_id  BIGINT      NOT NULL REFERENCES groups (id),
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX ix_group_member_user ON group_member (user_id);

-- OWNER는 그 그룹의 멤버여야 한다 (정책 1.3 제4항). 그룹과 첫 멤버를 한 트랜잭션에서 넣으므로 커밋 때 검사한다
ALTER TABLE groups
    ADD CONSTRAINT fk_groups_owner_member FOREIGN KEY (id, owner_id)
        REFERENCES group_member (group_id, user_id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE group_member_history
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id    BIGINT      NOT NULL REFERENCES groups (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('JOINED', 'LEFT', 'REMOVED')),
    actor_id    BIGINT      NOT NULL REFERENCES users (id),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_group_member_history_group ON group_member_history (group_id, occurred_at);

CREATE TRIGGER group_member_history_append_only
    BEFORE UPDATE OR DELETE
    ON group_member_history
    FOR EACH ROW
EXECUTE FUNCTION reject_append_only_modification();
CREATE TRIGGER group_member_history_no_truncate
    BEFORE TRUNCATE
    ON group_member_history
    FOR EACH STATEMENT
EXECUTE FUNCTION reject_append_only_modification();

CREATE TABLE invitation
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id     BIGINT       NOT NULL REFERENCES groups (id),
    -- 소문자로 정규화해 저장한다
    email        VARCHAR(254) NOT NULL,
    inviter_id   BIGINT       NOT NULL REFERENCES users (id),
    status       VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED', 'REPLACED')),
    expires_at   TIMESTAMPTZ  NOT NULL,
    responded_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_at   TIMESTAMPTZ
);

-- 같은 이메일에 대기 중인 초대는 그룹마다 1건이다 (정책 2.4 제5항)
CREATE UNIQUE INDEX uk_invitation_pending ON invitation (group_id, email) WHERE status = 'PENDING';
CREATE INDEX ix_invitation_email ON invitation (email) WHERE status = 'PENDING';

CREATE TABLE space
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id    BIGINT      NOT NULL REFERENCES groups (id),
    name        VARCHAR(50) NOT NULL,
    assignee_id BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_at  TIMESTAMPTZ,
    -- 담당자는 그 그룹의 멤버여야 한다 (정책 1.3 제4항)
    CONSTRAINT fk_space_assignee_member FOREIGN KEY (group_id, assignee_id)
        REFERENCES group_member (group_id, user_id)
);

CREATE INDEX ix_space_group ON space (group_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_space_assignee ON space (assignee_id) WHERE deleted_at IS NULL;

CREATE TABLE assignment_change
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    space_id     BIGINT      NOT NULL REFERENCES space (id),
    type         VARCHAR(20) NOT NULL CHECK (type IN ('INITIAL', 'HANDOVER', 'FORCED', 'REFILL')),
    from_user_id BIGINT REFERENCES users (id),
    to_user_id   BIGINT REFERENCES users (id),
    actor_id     BIGINT      NOT NULL REFERENCES users (id),
    reason       VARCHAR(500),
    occurred_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_assignment_change_space ON assignment_change (space_id, occurred_at);

CREATE TRIGGER assignment_change_append_only
    BEFORE UPDATE OR DELETE
    ON assignment_change
    FOR EACH ROW
EXECUTE FUNCTION reject_append_only_modification();
CREATE TRIGGER assignment_change_no_truncate
    BEFORE TRUNCATE
    ON assignment_change
    FOR EACH STATEMENT
EXECUTE FUNCTION reject_append_only_modification();

CREATE TABLE space_deletion_request
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    space_id     BIGINT      NOT NULL REFERENCES space (id),
    requester_id BIGINT      NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'INVALIDATED', 'EXPIRED')),
    expires_at   TIMESTAMPTZ NOT NULL,
    decided_by   BIGINT REFERENCES users (id),
    decided_at   TIMESTAMPTZ,
    reason       VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_at   TIMESTAMPTZ
);

-- 한 스페이스에서 대기 중인 삭제 요청은 1건이다 (정책 3.4 제5항)
CREATE UNIQUE INDEX uk_space_deletion_request_pending ON space_deletion_request (space_id) WHERE status = 'PENDING';
