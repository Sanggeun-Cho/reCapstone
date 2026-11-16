# 재구현 ERD

> Flyway 마이그레이션(`src/main/resources/db/migration`)과 같은 내용을 그린다. 마이그레이션을 추가할 때 함께 고친다.
> 모든 테이블은 감사 필드(`created_at`, `updated_at`, `created_by`, `updated_by`)와 `deleted_at`(soft delete)을 가진다. 아래 그림에서는 생략한다.

## S1 회원·인증 (`V2__users.sql`)

```mermaid
erDiagram
    users ||--o{ recovery_code : "복구 코드"

    users {
        bigint id PK
        varchar email "소문자 정규화. 활성 행끼리 유일 (deleted_at IS NULL 부분 인덱스)"
        varchar password_hash "BCrypt ({bcrypt} 접두어)"
        varchar name
        varchar work_phone "선택"
        varchar status "PENDING | ACTIVE"
        timestamptz email_verified_at
        varchar totp_secret "AES-GCM 암호문. 등록 시작 시 채움"
        timestamptz totp_enabled_at "확인을 마치면 기록 (= 2단계 인증 켜짐)"
        bigint totp_last_step "마지막으로 쓴 TOTP 구간 (재사용 방지)"
    }

    recovery_code {
        bigint id PK
        bigint user_id FK
        varchar code_hash "SHA-256 16진수"
        timestamptz used_at
    }
```

## S2 그룹·스페이스·권한 (`V3__groups_spaces.sql`)

```mermaid
erDiagram
    users ||--o{ groups : "OWNER"
    groups ||--o{ group_member : "현재 멤버"
    users ||--o{ group_member : ""
    group_member ||--o| groups : "OWNER는 멤버 (id, owner_id) 복합 FK, 커밋 때 검사"
    groups ||--o{ group_member_history : "가입·이탈 이력"
    groups ||--o{ invitation : "초대"
    groups ||--o{ space : ""
    group_member ||--o{ space : "담당자는 멤버 (group_id, assignee_id) 복합 FK"
    space ||--o{ assignment_change : "담당자 변경 이력"
    space ||--o{ space_deletion_request : "삭제 요청"

    groups {
        bigint id PK
        varchar name "1~50자"
        bigint owner_id FK "OWNER 1명 (정책 2.1)"
        timestamptz deleted_at "삭제 시각. 30일 보관 후 영구 삭제"
    }

    group_member {
        bigint group_id PK, FK
        bigint user_id PK, FK
        timestamptz joined_at
    }

    group_member_history {
        bigint id PK
        bigint group_id FK
        bigint user_id FK
        varchar type "JOINED | LEFT | REMOVED"
        bigint actor_id FK
        timestamptz occurred_at
    }

    invitation {
        bigint id PK
        bigint group_id FK
        varchar email "소문자 정규화. (group_id, email) 대기 중 1건 (부분 유니크)"
        bigint inviter_id FK
        varchar status "PENDING | ACCEPTED | DECLINED | CANCELED | REPLACED"
        timestamptz expires_at "7일. 만료는 이 값으로 판단"
        timestamptz responded_at
    }

    space {
        bigint id PK
        bigint group_id FK
        varchar name "1~50자"
        bigint assignee_id "담당자 0..1명"
        timestamptz deleted_at "삭제 시각. 30일 보관 후 영구 삭제"
    }

    assignment_change {
        bigint id PK
        bigint space_id FK
        varchar type "INITIAL | HANDOVER | FORCED | REFILL"
        bigint from_user_id FK
        bigint to_user_id FK
        bigint actor_id FK
        varchar reason "긴급 재지정 사유"
        timestamptz occurred_at
    }

    space_deletion_request {
        bigint id PK
        bigint space_id FK "대기 중 1건 (부분 유니크)"
        bigint requester_id FK
        varchar status "PENDING | APPROVED | REJECTED | WITHDRAWN | INVALIDATED | EXPIRED"
        timestamptz expires_at "7일"
        bigint decided_by FK
        timestamptz decided_at
        varchar reason "거절 사유"
    }
```

- `group_member`는 soft delete하지 않는다. 감사 필드와 `deleted_at`도 없다 (현재 멤버만 둔다).
- `group_member_history`, `assignment_change`는 추가 전용이다. 트리거 `reject_append_only_modification()`이 UPDATE·DELETE·TRUNCATE를 거부하고, 영구 삭제 트랜잭션(`app.purge = 'on'`)에서만 DELETE·TRUNCATE를 허용한다.
  - 이 두 테이블은 감사 필드와 `deleted_at`이 없다(코드 규약의 예외). 행을 바꿀 수 없으니 수정·삭제 정보가 생길 수 없고, 생성 정보는 `actor_id`·`occurred_at`이 담는다 (`DESIGN_REVIEW.md` 6.5).
- 보관 중인 스페이스에서 담당하던 멤버가 그룹을 떠나면 그 스페이스의 `assignee_id`를 비운다 (복합 FK, 정책 2.6 제4항).

## S3 파일·폴더 (`V4__drive.sql`)

```mermaid
erDiagram
    space ||--o{ drive_item : "파일·폴더"
    drive_item ||--o{ drive_item : "하위 항목 (parent_id)"
    drive_item |o--o{ drive_item : "원래 폴더 (original_parent_id)"
    users ||--o{ drive_item : "올린 사람·휴지통으로 옮긴 사람"

    drive_item {
        bigint id PK
        bigint space_id FK "(space_id, parent_id) 복합 FK: 부모는 같은 스페이스"
        bigint parent_id FK "null = 최상위. 휴지통 항목은 null(트리에서 뗌)"
        varchar type "FILE | FOLDER. 부모는 폴더만 (트리거)"
        varchar name "NFC, 1~255자"
        text name_key "NFC + 소문자. (space_id, parent_id, name_key) 부분 유니크(휴지통 제외, NULLS NOT DISTINCT)"
        int depth "최상위 1, 폴더는 10까지"
        bigint size "파일만"
        varchar content_type "현재 확장자 기준"
        varchar detected_type "업로드 때 내용으로 판별 (Tika)"
        boolean inline_viewable "바로 열기 가능 (확장자와 실제 형식 일치, PDF·이미지)"
        varchar storage_key UK "spaces/{spaceId}/files/{uuid}"
        bigint uploader_id FK
        timestamptz trashed_at "직접 휴지통으로 옮긴 항목에만"
        bigint trashed_by FK
        bigint original_parent_id FK "복구할 폴더. 영구 삭제되면 null"
    }

    storage_deletion {
        bigint id PK
        varchar storage_key "객체 하나 (둘 중 하나)"
        varchar key_prefix "스페이스 영구 삭제: spaces/{spaceId}/"
        timestamptz created_at
        int attempts
        varchar last_error
    }
```

- 휴지통 표시(`trashed_at`)는 직접 지운 항목에만 하고, 그 항목을 트리에서 떼어 `parent_id`를 비운다. 하위 항목은 그 항목 아래에 그대로 있어 함께 휴지통에 있는 것이 된다. 떼어 두므로 원래 폴더를 옮기거나 영구 삭제해도 따로 지운 항목은 영향을 받지 않는다 (`DESIGN_REVIEW.md` 6.8).
- `storage_deletion`은 작업 대기열이다. 감사 필드와 `deleted_at`이 없고, 객체 삭제가 끝나면 행을 지운다 (AGENTS.md 엔티티 예외).
- 영구 삭제 작업(S2)은 스페이스를 지울 때 `drive_item`을 함께 지우고 키 접두어를 대기열에 넣는다. 올린 파일이 남아 있는 탈퇴자의 이름은 지우지 않는다 (정책 7.9).
- 보관 중인 그룹의 멤버가 탈퇴하면 그 그룹의 멤버 행도 지우고 스페이스 담당을 비운다. 복구된 그룹에 탈퇴자가 다시 나오지 않게 하려는 것이다. 그 그룹의 OWNER인 경우는 OWNER 복합 FK 때문에 멤버 행을 그대로 두며, 그 그룹은 복구하지 않는다 (정책 2.6 제5항).

### DB 밖에 두는 상태 (Redis)
| 키 | 내용 | 수명 |
|---|---|---|
| `spring:session:*` | 로그인 세션 (Spring Session). 사용자 id로 색인 | 미사용 14일. 절대 수명 30일은 애플리케이션이 확인 |
| `auth:ott:{용도}:{토큰 해시}` | 메일 링크 토큰 → 사용자 id, 만료 시각 | 이메일 인증 24시간, 비밀번호 재설정 30분 |
| `auth:login-failures:{사용자 id}` | 연속 실패 횟수 | 1일 |
| `auth:login-lock:{사용자 id}` | 잠금 해제 시각 | 15분 |
