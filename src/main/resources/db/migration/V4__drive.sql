-- S3 파일·폴더 (정책 제8장, DESIGN_REVIEW.md 6.8)

-- 파일과 폴더를 한 테이블에 둔다. 같은 폴더 안에서 파일과 폴더가 한 이름 공간을 쓰기 때문이다 (정책 8.3 제2항)
CREATE TABLE drive_item
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    space_id           BIGINT       NOT NULL REFERENCES space (id),
    -- null이면 최상위. 휴지통으로 옮긴 항목은 트리에서 떼어 null로 두고, 원래 폴더는 original_parent_id에 남긴다
    parent_id          BIGINT,
    type               VARCHAR(10)  NOT NULL CHECK (type IN ('FILE', 'FOLDER')),
    -- NFC로 정규화한 표시 이름
    name               VARCHAR(255) NOT NULL,
    -- 이름 비교용 키: NFC + 소문자. 소문자로 바꾸면 길이가 늘 수 있어 길이를 제한하지 않는다
    name_key           TEXT         NOT NULL,
    -- 최상위가 1. 폴더는 10단계까지 (정책 8.3 제4항), 파일은 그 안에 둔다
    depth              INT          NOT NULL CHECK (depth BETWEEN 1 AND 11),
    size               BIGINT CHECK (size > 0),
    -- 현재 확장자로 정한 표시용 형식
    content_type       VARCHAR(255),
    -- 업로드할 때 내용으로 판별한 실제 형식
    detected_type      VARCHAR(255),
    inline_viewable    BOOLEAN      NOT NULL DEFAULT FALSE,
    storage_key        VARCHAR(255) UNIQUE,
    uploader_id        BIGINT REFERENCES users (id),
    trashed_at         TIMESTAMPTZ,
    trashed_by         BIGINT REFERENCES users (id),
    original_parent_id BIGINT,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT uk_drive_item_space_id UNIQUE (space_id, id),
    -- 부모 폴더는 같은 스페이스에 있어야 한다 (정책 8.1 제1항)
    CONSTRAINT fk_drive_item_parent FOREIGN KEY (space_id, parent_id) REFERENCES drive_item (space_id, id),
    -- 원래 폴더가 영구 삭제되면 복구할 때 최상위로 돌아간다 (정책 8.5 제4항)
    CONSTRAINT fk_drive_item_original_parent FOREIGN KEY (original_parent_id)
        REFERENCES drive_item (id) ON DELETE SET NULL,
    CONSTRAINT ck_drive_item_file CHECK (
        (type = 'FILE' AND storage_key IS NOT NULL AND size IS NOT NULL AND uploader_id IS NOT NULL)
            OR (type = 'FOLDER' AND storage_key IS NULL AND size IS NULL)),
    CONSTRAINT ck_drive_item_folder_depth CHECK (type = 'FILE' OR depth <= 10),
    CONSTRAINT ck_drive_item_trash CHECK ((trashed_at IS NULL) = (trashed_by IS NULL)),
    -- 휴지통 항목은 트리에서 떼어 둔다
    CONSTRAINT ck_drive_item_trash_detached CHECK (trashed_at IS NULL OR parent_id IS NULL)
);

-- 같은 폴더 안에서 이름이 겹치지 않는다. 휴지통 항목은 이름을 차지하지 않는다. 최상위(parent_id null)끼리도 겹치지 않는다
CREATE UNIQUE INDEX uk_drive_item_name ON drive_item (space_id, parent_id, name_key) NULLS NOT DISTINCT
    WHERE trashed_at IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_drive_item_parent ON drive_item (parent_id);
CREATE INDEX ix_drive_item_trash ON drive_item (space_id, trashed_at) WHERE trashed_at IS NOT NULL;
CREATE INDEX ix_drive_item_uploader ON drive_item (uploader_id);

-- 부모는 폴더여야 한다
CREATE OR REPLACE FUNCTION check_drive_item_parent_is_folder() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.parent_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM drive_item p WHERE p.id = NEW.parent_id AND p.type = 'FOLDER') THEN
        RAISE EXCEPTION 'parent % of drive_item is not a folder', NEW.parent_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER drive_item_parent_is_folder
    BEFORE INSERT OR UPDATE OF parent_id
    ON drive_item
    FOR EACH ROW
EXECUTE FUNCTION check_drive_item_parent_is_folder();

-- 저장소 객체 삭제 대기열. DB 행 삭제와 같은 트랜잭션에서 기록하고, 객체를 지운 뒤 행을 지운다 (작업 대기열, 감사 필드 없음)
CREATE TABLE storage_deletion
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 객체 하나의 키 또는 키 접두어(스페이스 영구 삭제) 중 하나
    storage_key VARCHAR(255),
    key_prefix  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    last_error  VARCHAR(1000),
    CONSTRAINT ck_storage_deletion_target CHECK ((storage_key IS NULL) <> (key_prefix IS NULL))
);
