package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.FileTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 인계 진행 중 잠금 (정책 4.2, 5.2, 8.5 제6항). 인계 절차(S5) 대신 확장 지점 대역으로 "인계 진행 중"을 만든다.
 */
class FileLockApiTest extends FileTestSupport {

    private long f;
    private long d;
    private long trashed;

    @BeforeEach
    void setUpItems() throws Exception {
        f = uploadOne(null, "f.txt", "x");
        d = folder(null, "D");
        trashed = uploadOne(null, "t.txt", "t");
        trash(trashed);
        handoverStatus.start(s, g, bob, carol);
    }

    private static void expectLocked(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
    }

    @Test
    @DisplayName("LOCK-01: 업로드, 폴더 생성, 이름 변경, 이동, 삭제는 각각 409, 변화 없음")
    void writesLocked() throws Exception {
        expectLocked(upload(BOB, s, file("a.txt", "x")));
        expectLocked(post("/api/v1/spaces/" + s + "/folders", BOB, body("\"name\":\"X\"")));
        expectLocked(patch(items(s) + "/" + f, BOB, body("\"name\":\"g.txt\"")));
        expectLocked(post(items(s) + "/" + f + "/move", BOB, body("\"targetFolderId\":" + d)));
        expectLocked(delete(items(s) + "/" + f, BOB));

        assertThat(item(f).getName()).isEqualTo("f.txt");
        assertThat(item(f).getParentId()).isNull();
        assertThat(item(f).isTrashed()).isFalse();
        assertThat(itemRepository.count()).isEqualTo(3);
        assertThat(objectCount(s)).isEqualTo(2);
    }

    @Test
    @DisplayName("LOCK-02: 휴지통 조회, 복구, 영구 삭제, 비우기는 각각 409")
    void trashLocked() throws Exception {
        expectLocked(get(trashUrl(s), BOB));
        expectLocked(post(trashUrl(s) + "/" + trashed + "/restore", BOB, null));
        expectLocked(delete(trashUrl(s) + "/" + trashed, BOB));
        expectLocked(delete(trashUrl(s), BOB));
        assertThat(item(trashed).isTrashed()).isTrue();
    }

    @Test
    @DisplayName("LOCK-03: 목록, 상세, 내려받기 주소는 200 (읽기는 허용)")
    void readsAllowed() throws Exception {
        get(items(s), BOB).andExpect(status().isOk());
        get(items(s) + "/" + f, CAROL).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/files/" + f + "/download-url", CAROL, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인계가 끝나면(대역 해제) 같은 작업이 성공한다")
    void unlockedAfterHandover() throws Exception {
        handoverStatus.reset();
        delete(items(s) + "/" + f, BOB).andExpect(status().isOk());
        get(trashUrl(s), BOB).andExpect(status().isOk());
    }
}
