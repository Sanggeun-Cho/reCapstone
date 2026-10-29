# 인계점 테스트 명세

> 용도: Thymeleaf 화면을 대체하는 **동작 계약**. 재구현 백엔드의 자동 테스트 목록이자, AI가 프론트엔드를 생성할 때 참고하는 기능 명세로 쓴다.
> 기준: 현행 코드의 **의도된 동작**을 기술한다. 현행 코드와 결과가 다른 케이스는 `⚠️ as-is:`로 현재 동작을 함께 적는다.
> 재설계 결정에 따라 바뀔 수 있는 케이스는 `🔁 D-n` (관련 [`DESIGN_REVIEW.md`](./DESIGN_REVIEW.md) 체크리스트 번호)으로 표시한다.
> URL은 현행 기준이다. 재설계에서 경로가 바뀌어도 시나리오는 그대로 유지한다.
> 확정된 규칙은 [`정책.md`](./정책.md)를 따른다. 결정이 반영된 케이스에는 `🔁` 표시를 지웠다.

## 0. 공통 픽스처

| 이름 | 설명 |
|---|---|
| `alice` | 그룹 G의 생성자이자 **OWNER**(관리자) |
| `bob` | G 멤버. 스페이스 S의 현재 **담당자** (인계자) |
| `carol` | G 멤버. 인수자 후보 |
| `dave` | G 멤버. S와는 무관 (같은 그룹의 제3자) |
| `mallory` | G에 속하지 않은 가입 사용자 (그룹 밖 제3자) |
| `anon` | 비로그인 |
| `G` / `S` | `alice`가 그룹 G와 스페이스 S를 생성한다. bob, carol, dave를 G 멤버로 추가하고, S의 첫 담당자로 bob을 지정한다 |
| 레벨 | **U** 도메인·서비스 단위 테스트 / **I** 통합 테스트(DB 포함) / **A** API 테스트(MockMvc, 인가·직렬화) |

공통 응답 규약: 성공 2xx (생성은 201). 실패 시 RFC 9457 `ProblemDetail` + 도메인 오류 코드(`code`). 아래 케이스의 오류 메시지는 `detail`에 들어간다. 원본의 실패 응답은 `{"message": "..."}`였다.

---

## 1. 인증 (AUTH)

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| AUTH-01 | A | – | 회원가입 `{username,password,name,email,phone,birth}` | 200, `{id}`, DB의 비밀번호는 BCrypt 해시 |
| AUTH-02 | A | username `u1` 존재 | 같은 username으로 가입 | 400 "이미 존재하는 아이디입니다" |
| AUTH-03 | I | email `e1` 존재 | 같은 email로 가입 | 실패 (⚠️ as-is: DB 유니크 제약 위반 → 400, 오류 메시지가 일관되지 않음) |
| AUTH-04 | A | 가입 완료 | `POST /api/login` 올바른 자격 증명 | 200, Refresh Token 발급 (⚠️ as-is: 응답 헤더 `RefreshToken`) `🔁 D-9` |
| AUTH-05 | A | – | 틀린 비밀번호 / 없는 username으로 로그인 | 401 |
| AUTH-06 | A | RT 보유 | `POST /api/auth`로 Access Token 재발급 | 200, AT 발급 |
| AUTH-07 | A | RT 없음 또는 위조 | AT 재발급 요청 | 401 (⚠️ as-is: RT가 없으면 `{prefix}null`로 200, 위조면 400) |
| AUTH-08 | U | 같은 유저가 다시 로그인 | 이전 RT로 재발급 요청 | 실패 (기존 RT는 폐기됨) `🔁 D-9 다중 기기` |
| AUTH-09 | A | 만료된 AT | 보호된 API 호출 | 401 |
| AUTH-10 | A | 유효한 RT를 AT 자리에 사용 | 보호된 API 호출 | 401 (⚠️ as-is: 통과될 수 있음) |
| AUTH-11 | A | 탈퇴한 사용자 | 로그인 | 401 (⚠️ as-is: 로그인 성공) |
| AUTH-12 | A | 로그인 상태 | 로그아웃 후 RT로 재발급 | 401 (⚠️ as-is: 로그아웃 API 없음) `🔁 D-9` |

## 2. 인가 매트릭스 (AUTHZ)

> 재설계의 핵심 테스트 묶음이며, [`정책.md`](./정책.md) 4.2 권한표를 그대로 옮긴 것이다. 각 칸은 기대 상태 코드이고, 현행과 다른 칸은 ⚠️로 표시한다.
> 평가 시점: **carol 열은 인계 진행 중(인수자)** 기준이다. 나머지 열은 평상시(인계 없음) 기준이다.
> 인계 진행 중의 쓰기 잠금(409)은 HO-30~34에서 따로 검증한다.

| 기능 | anon | mallory(그룹 밖) | dave(같은 그룹, 무관) | carol(인수자) | bob(담당자) | alice(OWNER) |
|---|---|---|---|---|---|---|
| 스페이스 상세 조회 | 401 ⚠️(200) | 403 ⚠️(200) | 403 | 200 | 200 | 200 |
| 스페이스 생성·업무명 수정·삭제 | 401 | 403 ⚠️(200) | 403 | 403 | 403 ⚠️(200) | 200 |
| 첫 담당자 지정 (담당자 없는 스페이스) | 401 | 403 | 403 | – | – | 200 |
| 긴급 담당자 재지정 (사유 필수) | 401 | 403 | 403 | 403 | 403 | 200 |
| 그룹명 수정·삭제, OWNER 양도 | 401 | 403 | 403 | 403 | 403 | 200 |
| 그룹 멤버 추가·제거 | 401 | 403 | 403 | 403 | 403 | 200 |
| 파일 목록 조회 | 401 | 403 | 403 | 200 | 200 | 200 |
| 파일 업로드·폴더 생성 | 401 | 403 | 403 | 403 | 200 | 403 |
| 파일 다운로드 (id) | 401 ⚠️(200) | 403 ⚠️(200) | 403 | 200 | 200 | 200 |
| 파일·폴더 이름 변경, 이동, 삭제 | 401 ⚠️(200) | 403 ⚠️(200) | 403 | 403 | 200 | 403 |
| 인수인계서 상세 조회 (id) | 401 | 403 ⚠️(200) | 403 | 200 | 200 | 200 |
| 인수인계서 작성·수정 | 401 | 403 | 403 | 403 | 200 | 403 |
| 인계 시작 | 401 | 403 | 403 | – | 200 | 403 ⚠️(200) |
| 서명 (자기 차례) | 401 | 403 | 403 | 200 | 200 | 200 |
| 서명 거부 (자기 차례, 사유 필수, 인계 취소) | 401 | 403 ⚠️(200) | 403 | 200 | 200 | 200 |
| 챗봇 질문·기록 조회 | 401 | 403 | 403 | 🔁 D-7 ⚠️(400) | 200 | 🔁 D-7 ⚠️(400) |
| 타인 회원정보 조회 | 401 | 403 ⚠️(200) | 403 | – | – | – |
| 타인 회원정보 수정·탈퇴 | 401 | 403 ⚠️(200) | 403 | – | – | – |

다음 API는 재구현에서 **만들지 않는다.** 존재하지 않음을 OpenAPI 스펙에서 확인한다.
- 멤버십 직접 생성·수정 API: 현행에서는 anon과 mallory도 200 ⚠️. 유스케이스 API로 대체한다.
- 서명 단계 강제 변경 API: 현행에서는 누구나 200 ⚠️.
- 권한(RBAC) 부여 API: 현행에서는 anon도 200 ⚠️. `🔁 D-8`에서 RBAC 테이블 폐기 여부를 확정한다.

## 3. 그룹 · 스페이스 (GRP / SPC)

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| GRP-01 | I | alice 로그인 | 그룹 생성 `{groupName, spaces:[{workName:"S1"}, {workName:"S2"}]}` | 그룹 1, 스페이스 2. owner=alice, created_by=alice, alice는 G 멤버. 두 스페이스 모두 담당자 미배정 (⚠️ as-is: 각 스페이스에 alice=관리자, 담당자는 이메일로 지정되거나 요청자) |
| GRP-02 | A | – | spaces가 빈 배열이거나 없음 | 400 "최소 하나의 스페이스를 포함해야..." |
| GRP-03 | I | mallory는 G 멤버가 아님 | alice가 S의 첫 담당자로 mallory 지정 | 400 "그룹 멤버만 담당자로 지정할 수 있습니다." (⚠️ as-is: 없는 이메일이면 요청자가 담당자가 됨) |
| GRP-04 | I | 스페이스 2개 중 2번째 생성 실패 | 그룹 생성 | 전체 롤백 (⚠️ as-is: 그룹과 1번째 스페이스가 남음) |
| GRP-05 | I | G에 스페이스 2개 | 그룹 삭제 | 그룹·스페이스·멤버십 모두 soft delete |
| GRP-06 | I | G의 스페이스 하나가 인계 진행 중 | 그룹 삭제 | 409, 전체 롤백 |
| GRP-07 | A | alice | 내가 OWNER인 그룹 목록 (cursor, perPage, orderWay) | OWNER인 그룹만, 커서 기준 정렬·개수 정확 |
| GRP-08 | I | alice가 OWNER | dave에게 OWNER 양도 | owner=dave, created_by=alice 그대로. 이후 alice의 그룹 관리 요청은 403 |
| GRP-09 | I | – | G 멤버가 아닌 mallory에게 OWNER 양도 | 400 |
| GRP-10 | I | – | OWNER alice가 양도 전에 그룹 탈퇴, 또는 멤버 목록에서 자신을 제거 | 409 |
| GRP-11 | I | bob이 S 담당자 | alice가 G에서 bob 제거 | 409 (먼저 인계하거나 긴급 재지정해야 함. DB 복합 FK로도 거부됨). SPC-12 이후에는 제거 성공 |
| GRP-12 | I | DB 직접 삽입 | 그룹 멤버가 아닌 사용자를 `space.assignee_id`나 `group.owner_id`에 넣음 | FK 제약 위반 |
| GRP-13 | I | dave가 G 멤버 | alice가 dave 제거 → 다시 추가 → dave 스스로 탈퇴 | `group_member`에는 현재 상태만 남음. `group_member_history`에는 JOINED → REMOVED → JOINED → LEFT 네 건이 실행자·시각과 함께 쌓임 |
| GRP-14 | I | 이력 존재 | `group_member_history` 행을 UPDATE 또는 DELETE (DB 직접) | DB 트리거가 거부 |
| GRP-15 | I | 제거된 멤버 | 그 사용자를 S의 담당자로 지정 (DB 직접 삽입 포함) | 애플리케이션 400, DB에서도 FK 위반 |
| SPC-01 | U | – | 스페이스 생성 | `spaceCode`는 서버가 UUID로 발급 (요청값 무시) `🔁 1.9` |
| SPC-02 | I | 진행 중 인계 있음 | 스페이스 수정·삭제 | 409 "현재 인수인계가 진행 중이므로..." (⚠️ as-is 400) |
| SPC-03 | I | – | 스페이스 삭제 | 스페이스와 하위 데이터 처리 `🔁 D-11` (별도의 스페이스 멤버십은 없음) |
| SPC-04 | A | bob | 대시보드 내 스페이스 (page) | 본인이 참여한 스페이스(담당자이거나 진행 중 인계의 인수자), 페이지당 7개, `{callPage,totalPage,listCount,list}` |
| SPC-05 | U | listCount=0 | 페이지 응답 | totalPage=1, callPage=1 |
| SPC-06 | U | callPage > totalPage | 페이지 응답 | callPage=totalPage로 보정 |
| SPC-07 | A | bob | 프로필 내 스페이스 (cursor) | 본인이 참여한 스페이스 |
| SPC-08 | A | alice | 그룹 관리 화면의 스페이스 목록 (groupId) | G의 모든 스페이스. 스페이스명, 담당자명(미배정이면 null), 진행 중 인계 id 포함 |
| SPC-09 | A | dave (G 멤버) | G의 스페이스 목록 또는 S 상세 조회 | 403. 대시보드에도 S가 나타나지 않음 |
| SPC-10 | I | S2 담당자 없음 | alice가 S2의 첫 담당자로 carol 지정 | S2.assignee=carol. `AssignmentChange(INITIAL)` 기록, 인계 프로세스 없음 |
| SPC-11 | I | S 담당자=bob | alice가 첫 담당자 지정 API로 carol 지정 | 409 "담당자 변경은 인계 절차 또는 긴급 재지정으로만 할 수 있습니다." |
| SPC-12 | I | S 담당자=bob, 인계 없음 | alice가 사유와 함께 carol로 긴급 재지정 | S.assignee=carol, `AssignmentChange(FORCED, from=bob, to=carol, 실행자=alice, 사유)` 기록. bob은 이후 S 접근 시 403 |
| SPC-13 | I | bob→carol 인계 진행 중 | alice가 dave로 긴급 재지정 | 진행 중 인계가 "긴급 재지정" 사유로 취소되고 잠금이 풀림. carol 읽기 권한 소멸, S.assignee=dave |
| SPC-14 | A | – | 사유 없이 긴급 재지정 / G 멤버가 아닌 mallory로 재지정 | 400 / 400 |
| SPC-15 | I | – | alice가 대상 없이(null) 긴급 재지정 → 이후 carol 지정 | 첫 단계에서 S 담당자 미배정, `FORCED(to=null)` 기록. 다음 지정은 `REFILL`로 기록 (`INITIAL` 아님) |
| SPC-16 | A | S | 담당자 변경 이력 조회 (alice) | INITIAL → HANDOVER → FORCED → REFILL 순서로 유형·이전·새 담당자·실행자·사유·시각 반환 |
| SPC-17 | I | 담당자가 있었던 적이 없는 스페이스 | 첫 지정 | `INITIAL`로 기록 |
| SPC-18 | I | 변경 이력 존재 | `assignment_change` 행을 UPDATE 또는 DELETE (DB 직접) | DB 트리거가 거부 |

## 4. 인계 프로세스 (HO)

### 4.1 시작
| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| HO-01 | I | bob의 파일이 모두 인수인계서에 첨부됨 | bob이 carol을 인수자로 인계 시작 | 서명 상태 `ASSIGNOR_TURN`. 서명자 3명(인계자 bob, 인수자 carol, 관리자 alice = 시작 시점의 OWNER). 스페이스가 즉시 읽기 전용이 되고, carol은 S 읽기 가능 |
| HO-02 | I | 진행 중 인계 있음 | 인계 다시 시작 | 409 "현재 진행 중인 인수인계가 있습니다..." |
| HO-03 | I | – | 인수자로 bob 자신을 지정 | 400 "본인 스스로에게 인계할 수 없습니다." |
| HO-04 | A | mallory는 G 멤버가 아님 | mallory를 인수자로 지정 | 400 "그룹 멤버에게만 인계할 수 있습니다." `🔁 D-3 그룹 초대` |
| HO-05 | I | bob의 파일 a.pdf가 첨부되지 않음 | 인계 시작 | 400, 메시지에 `a.pdf` 포함, 인계 프로세스 미생성 `🔁 D-4` |
| HO-06 | I | 첨부되지 않은 파일이 **삭제됨** | 인계 시작 | 성공 (⚠️ as-is: 삭제된 파일도 누락으로 판정) |
| HO-07 | I | carol이 과거에 S의 담당자였음 | 인계 시작 | 성공. 별도의 멤버십 행을 만들거나 재사용하지 않음 |
| HO-08 | – | (삭제) | – | OWNER는 `owner_id NOT NULL`로 항상 존재하므로 "관리자 없음" 상태가 생길 수 없음 (GRP-12) |
| HO-09 | I | 동시에 두 요청으로 인계 시작 | – | 정확히 1건만 성공. 스페이스당 진행 중 인계 1건 유니크 제약 (⚠️ as-is: 경쟁 상태 가능) |
| HO-08a | A | – | OWNER alice가 S의 인계 시작 (alice는 담당자 아님) | 403 (⚠️ as-is: 200) |

### 4.2 서명 (현행 단위 테스트 8건 포함)
| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| HO-10 | U | ASSIGNOR_TURN | bob 서명 | ASSIGNEE_TURN, bob signedAt 기록 |
| HO-11 | U | ASSIGNEE_TURN | carol 서명 | ADMIN_TURN |
| HO-12 | U | ADMIN_TURN | alice 서명 | COMPLETED. `S.assignee`=carol. bob은 이후 S 접근 시 403 |
| HO-13 | U | ASSIGNOR_TURN | carol 서명 | 400 "현재 차례에 서명할 권한이 없습니다." |
| HO-14 | U | – | mallory 또는 dave 서명 | 403 (⚠️ as-is: 400) |
| HO-15 | U | COMPLETED | 서명 | 400 "이미 완료된 인계 절차입니다." |
| HO-16 | U | 거부(취소)됨 | 서명 | 400 "이미 취소된 인계 절차입니다." |
| HO-17 | U | alice(OWNER)가 S2의 담당자를 겸함 | alice가 dave에게 인계를 시작하고, ASSIGNOR_TURN과 ADMIN_TURN에서 각각 서명 | 두 번 모두 성공. 첫 서명 뒤 상태는 ASSIGNEE_TURN이며, 서명 한 번으로 ADMIN_TURN까지 넘어가지 않음 |
| HO-18 | A | 인계 프로세스 상세 조회 | – | `{stepStatus, spaceId, assignorId, assigneeId, adminId, *SignedAt, rejection}`. 권한 검사를 조회보다 먼저 함 (⚠️ as-is: 조회 후 멤버 검사) |
| HO-19 | I | COMPLETED 이후 | carol이 챗봇·파일 사용 | 허용 (carol이 새 담당자) |

### 4.3 서명 거부 (= 인계 취소)
| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| HO-20 | I | 각 단계(ASSIGNOR/ASSIGNEE/ADMIN_TURN) | 그 단계의 서명자(bob / carol / alice)가 사유와 함께 거부 | 프로세스는 거부(취소) 상태로 **보존**되고 거부자·사유·시각이 기록됨. 잠금 해제. carol의 S 읽기 권한 소멸 (⚠️ as-is: 서명 테이블 soft delete) |
| HO-21 | I | COMPLETED | 거부 | 400 "이미 완료된 인계는 거부할 수 없습니다." |
| HO-22 | A | – | mallory 또는 dave가 거부 | 403 (⚠️ as-is: 누구나 취소 가능 200) |
| HO-23 | I | 거부 이후 | 인계 다시 시작 | 성공. 이전 거부 건은 이력으로 남음 |
| HO-24 | A | – | 사유 없이(공백) 거부 | 400 |
| HO-25 | U | ASSIGNOR_TURN (carol 차례 아님) | carol이 거부 | 400 "현재 차례에 거부할 권한이 없습니다." 인계 상태 변화 없음 |
| HO-26 | U | ASSIGNEE_TURN (bob은 이미 서명) | bob이 거부 | 400. 인계자는 자기 서명 뒤에는 취소할 수 없음 |

### 4.4 인계 진행 중 잠금 (인계를 시작한 순간부터)
| ID | 레벨 | 행위 (진행 중 인계 있음, ASSIGNOR_TURN 포함) | 기대 결과 |
|---|---|---|---|
| HO-30 | I | 파일 업로드 / 이름 변경 / 삭제 / 이동 | 409 (⚠️ as-is 400) |
| HO-31 | I | 폴더 생성 / 이름 변경 / 삭제 / 이동 | 409 |
| HO-32 | I | 스페이스 수정 / 삭제 | 409 |
| HO-33 | I | 인계 완료 또는 거부 후 같은 작업 | 성공 |
| HO-34 | I | ASSIGNOR_TURN에서 bob이 인수인계서 수정 | 409 "인계 진행 중에는 수정할 수 없습니다. 인계를 취소한 뒤 수정하세요." (⚠️ as-is: ASSIGNOR_TURN까지 수정 가능) |

## 5. 인수인계서 (DOC)

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| DOC-01 | I | bob(담당자) | spaceId로 문서 생성, 제목·내용 생략, role="회계" | title="회계 인수인계서", text=`{"title":"회계 인수인계서","role":"회계","modules":[]}` |
| DOC-02 | U | title·role 모두 공백 | 생성 | title="인수인계서" |
| DOC-03 | I | bob의 문서가 이미 있음 | 다시 생성 | 400 "이미 해당 스페이스에 인수인계 문서가 존재합니다..." `🔁 D-5` |
| DOC-04 | I | carol(인수자, 인계 진행 중) | 문서 생성 | 403 (⚠️ as-is: 400 "활성 담당자만...") |
| DOC-05 | I | 인계 진행 중 (어느 단계든) | bob이 새 문서 생성 | 409 "인계 진행 중에는 수정할 수 없습니다..." (⚠️ as-is: ASSIGNOR_TURN에서는 생성 가능) |
| DOC-06 | I | 파일 f1, f2 | 생성 시 `referencedFileIds=[f1]` | f1 첨부됨, f2 미첨부 `🔁 D-4` |
| DOC-07 | I | f1 첨부됨 | 수정 시 `referencedFileIds=[f2]` | f1 미첨부, f2 첨부 |
| DOC-08 | I | f1 첨부됨 | 수정 시 `referencedFileIds` 생략 (제목만 수정) | 첨부 상태 유지 (⚠️ as-is: 전부 해제) |
| DOC-09 | I | – | 모듈 JSON만 수정 | text만 바뀌고 title·role은 그대로 |
| DOC-10 | I | – | 다른 폴더로 이동 / targetFolderId=null | folderId 변경 / 루트로 이동 |
| DOC-11 | I | – | bob이 문서 삭제 | soft delete, 이후 다시 생성 가능 (⚠️ as-is: 항상 실패) |
| DOC-12 | A | – | 내 문서 조회 (userSpaceId) — 문서 없음 | 404 |
| DOC-13 | A | – | 스페이스 루트 / 폴더별 문서 목록 | 삭제되지 않은 문서, 최신순 |
| DOC-14 | U | 각 인계 상태별 | 정책 조회 | 아래 정책 표와 같은 `canEdit / canGeneratePdf / lockReason` |

**DOC-14 정책 표** (canEdit 열은 확정. PDF 관련 열은 `🔁 D-5` 서버 생성과 스냅샷 결정에 따라 바뀐다)
| 상태 | 요청자 | canEdit | canGeneratePdf | lockReason |
|---|---|---|---|---|
| 진행 중 인계 없음 | 담당자 | true | false | "전임자 서명 완료 상태에서만 PDF를 생성할 수 있습니다." |
| 진행 중 인계 없음 | OWNER | false | false | "담당자만 수정할 수 있습니다." |
| ASSIGNOR_TURN | – | false ⚠️(as-is true) | false | "인계 진행 중에는 수정할 수 없습니다. 인계를 취소한 뒤 수정하세요." |
| ASSIGNEE_TURN, PDF 미생성 | bob 또는 carol | false | true | null |
| ASSIGNEE_TURN, PDF 미생성 | alice | false | false | "전임자 또는 인수자만 PDF를 생성할 수 있습니다." |
| ASSIGNEE_TURN, PDF 생성됨 | – | false | false | "PDF가 이미 생성되었습니다." |
| ADMIN_TURN | – | false | false | "인수자 서명 완료 이후에는 인수인계서가 잠깁니다." |
| 인계 완료 후 이전 담당자 | – | – | – | 접근 불가 (403) |

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| DOC-20 | I | canEdit=false | 수정 / 모듈 수정 / 이동 | 인계 잠금이면 409, 권한 없음이면 403. 응답에 lockReason 포함 (⚠️ as-is: 400) |
| DOC-21 | I | ASSIGNEE_TURN, bob | PDF 저장 (folderId 생략) | 루트의 "인수인계서" 시스템 폴더에 PDF 저장(폴더가 없으면 생성), pdfGeneratedAt·By 기록 |
| DOC-22 | I | DOC-21 이후 | PDF 다시 저장 | 400 "PDF가 이미 생성되었습니다." |
| DOC-23 | I | mdFile 포함 | PDF 저장 | 커밋 이후 RAG 임베딩 작업이 등록됨 |
| DOC-24 | I | 다른 스페이스의 spaceId 전달 | PDF 저장 | 400 "인수인계 문서와 스페이스 정보가 일치하지 않습니다." |
| DOC-25 | I | 같은 문서에 동시 PDF 저장 2건 | – | 1건만 성공 (비관적 락) |
| DOC-26 | U | 모듈 JSON 스키마 위반 | 저장 | 400 `🔁 D-5` (as-is: 검증 없음) |

## 6. 파일 · 폴더 (FILE)

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| FILE-01 | I | bob | multipart 업로드 (파일 2개, folderId 생략) | 파일 2건이 루트에 저장. 저장명 UUID, 원본명·크기 보존, 첨부 상태=미첨부, 파일마다 임베딩 작업 등록 |
| FILE-02 | I | – | 빈 파일 포함 업로드 | 빈 파일은 건너뜀 |
| FILE-03 | I | – | 확장자 없는 파일 | 저장명이 `uuid` 형태 (⚠️ as-is: `uuid.`) |
| FILE-04 | A | – | 목록 조회 (folderId 생략) | 루트 폴더가 먼저, 그다음 파일. 각각 최신순. `{id,type:FOLDER|FILE,name,fileUrl,size,uploaderName,createdAt}` |
| FILE-05 | A | – | 다운로드 `mode=download` | `Content-Disposition: attachment; filename="<UTF-8 URL 인코딩>"`, 추정한 Content-Type |
| FILE-06 | A | docx 파일 | `mode=view` | PDF로 변환, `application/pdf`, `inline`, 파일명 `.pdf` |
| FILE-07 | A | png 파일 | `mode=view` | 원본, `inline` |
| FILE-08 | I | – | 파일 이름 변경 / 삭제 | 반영 / soft delete (목록에서 제외) |
| FILE-09 | I | – | 폴더 생성 (parentId) → 하위 목록 조회 | 트리 구조 반영 |
| FILE-10 | I | – | 존재하지 않는 폴더로 이동 | 400 "목적지 폴더가 존재하지 않습니다." |
| FILE-11 | I | – | 폴더를 자기 자신으로 이동 | 400 "자기 자신으로 이동할 수 없습니다." |
| FILE-12 | I | A ⊃ B | A를 B 아래로 이동 | 400 (⚠️ as-is: 허용되어 순환 발생) |
| FILE-13 | I | 하위 항목이 있는 폴더 | 삭제 | 하위까지 삭제하거나 거부 (⚠️ as-is: 부모만 삭제) `🔁 D-6` |
| FILE-14 | I | 다른 스페이스의 폴더 | 그 폴더로 이동 | 400 |

## 7. 챗봇 (CHAT) — Spring AI + Ollama 대체 기준

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| CHAT-01 | I | bob, 모델 스텁 | 질문 `{question, spaceId}` | `{answer, sources:[{source,page}]}` 반환, 질문·답변·출처가 기록에 저장 |
| CHAT-02 | A | – | question 공백 / spaceId 누락 | 400 |
| CHAT-03 | I | 스페이스 S1, S2에 각각 문서 | S1에서 질문 | 검색 결과에 S2 문서가 **절대** 포함되지 않음 (spaceId 메타데이터 필터) |
| CHAT-04 | I | 모델 타임아웃 | 질문 | 504 또는 503, 기록 저장 안 함 (⚠️ as-is: 400 + "timeout/unreachable") |
| CHAT-05 | A | 기록 10건 | 기록 조회 size=4 (cursor 없음) | 최근 4건을 오래된 → 최신 순서로 반환 |
| CHAT-06 | A | – | 기록 조회 cursor=c | id < c 중 최근 4건 |
| CHAT-07 | I | 인계 완료 후 carol | 기록 조회 | `🔁 D-7` (as-is: bob의 기록은 보이지 않음) |
| CHAT-08 | I | 파일 업로드 | – | 임베딩 작업 상태 `PENDING → DONE`, 실패 시 `FAILED` + 재시도 `🔁 D-7` |
| CHAT-09 | I | 파일 삭제 | – | 해당 파일의 벡터 제거 `🔁 D-7` |
| CHAT-10 | U | sources 직렬화 실패한 과거 데이터 | 기록 조회 | sources=null, 나머지 필드는 정상 반환 |

## 8. 사용자 (USER)

| ID | 레벨 | 전제 | 행위 | 기대 결과 |
|---|---|---|---|---|
| USER-01 | A | bob | 내 정보 조회 (id 생략) | `{id, username, name, email, phone, birth, createdAt}` (비밀번호 제외) |
| USER-02 | I | bob | 이름·전화번호 수정 | 지정한 필드만 변경 |
| USER-03 | I | bob | 비밀번호 변경 후 새 비밀번호로 로그인 | 성공 (⚠️ as-is: 해시가 적용되지 않아 실패) |
| USER-04 | I | bob | 탈퇴 | 사용자와 그룹 멤버십 soft delete. 담당자, OWNER, 진행 중 인계 당사자라면 `🔁 1.9` (GRP-10·11과 같은 기준이 유력) |
| USER-05 | A | – | id 없이 수정 요청 | 본인 수정 (⚠️ as-is: id=null이면 NPE) |
| USER-06 | A | – | 사용자 목록·검색 (name) | `🔁 D-8` 공개 범위 결정 (⚠️ as-is: SQL 오류) |

## 9. 비기능 (NFR)

| ID | 레벨 | 항목 | 기대 결과 |
|---|---|---|---|
| NFR-01 | A | 업로드 크기 | 200MB 초과 시 413 |
| NFR-02 | I | 한글 파일명 | 업로드·목록·다운로드 전 구간에서 깨지지 않음 |
| NFR-03 | A | CORS | 허용된 origin만 통과, 토큰 헤더 노출 |
| NFR-04 | I | 목록 API 쿼리 수 | 목록 1회 조회당 쿼리 수가 상수 (N+1 없음) |
| NFR-05 | A | OpenAPI | `/v3/api-docs`에 모든 엔드포인트, 요청·응답·오류 스키마, enum 값 노출 |
| NFR-06 | I | 마이그레이션 | 빈 DB에서 Flyway 적용 후 애플리케이션 기동 성공 |
| NFR-07 | – | 로그 | 요청 ID 포함, 비밀번호·토큰은 로그에 남지 않음 |
