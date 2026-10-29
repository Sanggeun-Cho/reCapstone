CREATE EXTENSION IF NOT EXISTS vector;

-- 추가 전용 테이블(group_member_history, assignment_change)에 붙이는 공통 트리거 함수.
-- 테이블마다 BEFORE UPDATE OR DELETE (FOR EACH ROW)와 BEFORE TRUNCATE (FOR EACH STATEMENT) 트리거로 연결한다.
CREATE FUNCTION reject_append_only_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is not allowed on append-only table %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;
