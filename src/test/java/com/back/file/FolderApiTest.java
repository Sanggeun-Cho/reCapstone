package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.FileTestSupport;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 폴더 생성, 이름 변경, 이동 (정책 8.3, 8.6 제2항). */
class FolderApiTest extends FileTestSupport {

    private String folders() {
        return "/api/v1/spaces/" + s + "/folders";
    }

    private org.springframework.test.web.servlet.ResultActions rename(long itemId, String name) throws Exception {
        return patch(items(s) + "/" + itemId, BOB, body("\"name\":\"" + name + "\""));
    }

    private org.springframework.test.web.servlet.ResultActions move(long itemId, Long target) throws Exception {
        return post(items(s) + "/" + itemId + "/move", BOB, body("\"targetFolderId\":" + target));
    }

    @Test
    @DisplayName("FILE-09: 폴더 A를 만들고 그 아래 B를 만들면 A 목록에 B")
    void createTree() throws Exception {
        String a = post(folders(), BOB, body("\"name\":\"A\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FOLDER"))
                .andReturn().getResponse().getContentAsString();
        long aId = ((Number) com.jayway.jsonpath.JsonPath.read(a, "$.id")).longValue();
        post(folders(), BOB, body("\"parentId\":" + aId + ",\"name\":\"B\"")).andExpect(status().isCreated());
        get(items(s) + "?folderId=" + aId, BOB).andExpect(jsonPath("$[*].name", contains("B")));
    }

    @Test
    @DisplayName("FILE-16: a.txt가 있을 때 폴더 A.TXT 생성 / 다른 파일을 a.TXT로 변경 / 같은 이름이 있는 폴더로 이동은 409")
    void nameConflicts() throws Exception {
        uploadOne(null, "a.txt", "x");
        long other = uploadOne(null, "b.txt", "y");
        long d = folder(null, "D");
        long inD = uploadOne(d, "a.txt", "z");

        post(folders(), BOB, body("\"name\":\"A.TXT\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
        rename(other, "a.TXT").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
        move(inD, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
    }

    @Test
    @DisplayName("FILE-16b: 자기 이름을 A.txt로 바꾸면 200 (표기만 바뀜)")
    void renameCaseOnly() throws Exception {
        long a = uploadOne(null, "a.txt", "x");
        rename(a, "A.txt").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("A.txt"));
    }

    @Test
    @DisplayName("FILE-16c: 휴지통의 a.txt는 이름을 차지하지 않는다 (같은 이름 폴더 생성 201)")
    void trashedNameFree() throws Exception {
        trash(uploadOne(null, "a.txt", "x"));
        post(folders(), BOB, body("\"name\":\"a.txt\"")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FILE-07c: 실제 PNG a.png를 a.txt로 바꾸면 내려받기 전용, 다시 a.png로 바꾸면 바로 열기 가능")
    void renameRejudges() throws Exception {
        long a = uploadOne(s, null, "a.png", PNG);
        rename(a, "a.txt").andExpect(jsonPath("$.inlineViewable").value(false))
                .andExpect(jsonPath("$.contentType").value("text/plain"));
        rename(a, "a.png").andExpect(jsonPath("$.inlineViewable").value(true))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    @DisplayName("FILE-07d: HTML 내용의 b.txt를 b.png로 바꿔도 내려받기 전용, INLINE 요청은 400")
    void renameDoesNotMakeHtmlInline() throws Exception {
        long b = uploadOne(s, null, "b.txt", HTML);
        rename(b, "b.png").andExpect(jsonPath("$.inlineViewable").value(false));
        post("/api/v1/spaces/" + s + "/files/" + b + "/download-url", BOB, body("\"disposition\":\"INLINE\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INLINE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("FILE-18: 깊이 10 폴더 안에 폴더를 만들면 400 FOLDER_DEPTH_EXCEEDED. 파일 업로드는 허용")
    void depthLimit() throws Exception {
        long deepest = folderChain(null, 10, "d");
        assertThat(item(deepest).getDepth()).isEqualTo(10);
        post(folders(), BOB, body("\"parentId\":" + deepest + ",\"name\":\"X\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOLDER_DEPTH_EXCEEDED"));
        upload(BOB, s, List.of(file("f.txt", "x")), "folderId=" + deepest).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FILE-18b: 깊이 2 트리 P를 깊이 8 폴더 아래로는 200, 깊이 9 폴더 아래로는 400")
    void moveDepthLimit() throws Exception {
        long p = folder(null, "P");
        long pChild = folder(p, "P1");
        long q8 = folderChain(null, 8, "q");
        long q9 = folder(q8, "q9");

        move(p, q9).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FOLDER_DEPTH_EXCEEDED"));
        move(p, q8).andExpect(status().isOk());
        assertThat(item(p).getDepth()).isEqualTo(9);
        assertThat(item(pChild).getDepth()).isEqualTo(10);
    }

    @Test
    @DisplayName("FILE-18c: 휴지통 하위는 세지 않는다. P 아래 휴지통 폴더 T(5단계)가 있어도 P를 깊이 6 아래로 옮길 수 있다")
    void moveIgnoresTrashed() throws Exception {
        long p = folder(null, "P");
        long tTop = folder(p, "T");
        folderChain(tTop, 4, "t");
        trash(tTop);
        long six = folderChain(null, 6, "s");

        move(p, six).andExpect(status().isOk());
        assertThat(item(p).getDepth()).isEqualTo(7);
        // 휴지통 항목은 트리에서 떼어 두었으므로 깊이가 그대로다. 복구할 때 다시 계산한다
        assertThat(item(tTop).getParentId()).isNull();
        assertThat(item(tTop).getOriginalParentId()).isEqualTo(p);
    }

    @Test
    @DisplayName("FILE-10·22: 없는 폴더로 이동은 404, 파일을 다른 파일 아래로는 400 INVALID_MOVE_TARGET")
    void invalidTargets() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        long g2 = uploadOne(null, "g.txt", "y");
        move(f, 999_999L).andExpect(status().isNotFound());
        move(f, g2).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
    }

    @Test
    @DisplayName("FILE-11·12: 폴더를 자기 자신으로, A를 그 하위 C 아래로 옮기면 400 INVALID_MOVE_TARGET")
    void cycles() throws Exception {
        long a = folder(null, "A");
        long b = folder(a, "B");
        long c = folder(b, "C");
        move(a, a).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
        move(a, c).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
    }

    @Test
    @DisplayName("FILE-14: 다른 스페이스의 폴더로 옮기면 404")
    void otherSpace() throws Exception {
        assign(alice, s2, bob);
        long other = as(bob, () -> fileService.createFolder(bob, s2,
                new com.back.file.api.FileDto.FolderReq(null, "O")));
        long f = uploadOne(null, "f.txt", "x");
        move(f, other).andExpect(status().isNotFound());
        assertThat(item(f).getSpaceId()).isEqualTo(s);
    }

    @Test
    @DisplayName("FILE-23: 하위 폴더를 최상위로 옮기면 하위 항목까지 깊이가 갱신된다")
    void moveToRoot() throws Exception {
        long a = folder(null, "A");
        long b = folder(a, "B");
        long f = uploadOne(b, "f.txt", "x");
        move(b, null).andExpect(status().isOk());
        assertThat(item(b).getParentId()).isNull();
        assertThat(item(b).getDepth()).isEqualTo(1);
        assertThat(item(f).getDepth()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/b", "CON", "끝 공백 ", "점."})
    @DisplayName("FILE-24: 이름 규칙 위반으로 변경·생성하면 400 INVALID_ITEM_NAME")
    void invalidNames(String name) throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        rename(f, name).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ITEM_NAME"));
        post(folders(), BOB, body("\"name\":\"" + name + "\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ITEM_NAME"));
    }

    @Test
    @DisplayName("FILE-25: 같은 이름 폴더를 동시에 만들면 1건 201, 1건 409")
    void concurrentCreate() throws Exception {
        session(BOB);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> create = () -> {
            barrier.await();
            return post(folders(), BOB, body("\"name\":\"같은 폴더\"")).andReturn().getResponse().getStatus();
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(create);
            Future<Integer> second = executor.submit(create);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("이름 변경·이동·생성은 담당자만 (alice 403, dave 404)")
    void writePermission() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        patch(items(s) + "/" + f, ALICE, body("\"name\":\"g.txt\"")).andExpect(status().isForbidden());
        patch(items(s) + "/" + f, DAVE, body("\"name\":\"g.txt\"")).andExpect(status().isNotFound());
        post(folders(), ALICE, body("\"name\":\"X\"")).andExpect(status().isForbidden());
        delete(items(s) + "/" + f, ALICE).andExpect(status().isForbidden());
    }
}
