package com.back.file;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.file.domain.DriveItem;
import com.back.support.FileTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 폴더 내용과 항목 상세 (정책 8.3, 8.5, 7.8 제3항). */
class ItemListApiTest extends FileTestSupport {

    @Test
    @DisplayName("FILE-04: 폴더 먼저, 그다음 파일, 각각 이름순. 휴지통 항목 없음, 저장소 키·주소 없음")
    void order() throws Exception {
        folder(null, "나");
        folder(null, "가");
        uploadOne(null, "b.txt", "b");
        uploadOne(null, "A.txt", "a");
        long z = uploadOne(null, "z.txt", "z");
        trash(z);

        get(items(s), BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("가", "나", "A.txt", "b.txt")))
                .andExpect(jsonPath("$[*].type", contains("FOLDER", "FOLDER", "FILE", "FILE")))
                .andExpect(jsonPath("$[2].size").value(1))
                .andExpect(jsonPath("$[2].contentType").value("text/plain"))
                .andExpect(jsonPath("$[2].uploaderName").value("밥"))
                .andExpect(jsonPath("$[2].uploaderWithdrawn").value(false))
                .andExpect(jsonPath("$[2].createdAt").exists())
                .andExpect(jsonPath("$[2]", not(hasKey("storageKey"))))
                .andExpect(jsonPath("$[2]", not(hasKey("url"))));
    }

    @Test
    @DisplayName("FILE-04b: carol(인수자)·alice는 200, dave·mallory는 404, 비로그인 401")
    void readPermission() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        get(items(s), CAROL).andExpect(status().isOk());
        get(items(s), ALICE).andExpect(status().isOk());
        get(items(s), DAVE).andExpect(status().isNotFound());
        get(items(s), MALLORY).andExpect(status().isNotFound());
        get(items(s), ANON).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FILE-04c: 깊이 3 폴더의 파일 상세는 경로 [루트폴더, 중간, 부모]")
    void detailPath() throws Exception {
        long root = folder(null, "루트폴더");
        long middle = folder(root, "중간");
        long parent = folder(middle, "부모");
        long f = uploadOne(parent, "f.txt", "x");

        get(items(s) + "/" + f, ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.name").value("f.txt"))
                .andExpect(jsonPath("$.parentId").value(parent))
                .andExpect(jsonPath("$.path[*].name", contains("루트폴더", "중간", "부모")))
                .andExpect(jsonPath("$.path[*].id", contains((int) root, (int) middle, (int) parent)));
        get(items(s) + "?folderId=" + middle, BOB).andExpect(jsonPath("$[*].name", contains("부모")));
    }

    @Test
    @DisplayName("FILE-04d: 한 폴더의 300개를 페이징 없이 한 번에 돌려준다")
    void noPaging() throws Exception {
        List<DriveItem> folders = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            folders.add(DriveItem.folder(s, null, 1, String.format("f%03d", i)));
        }
        as(bob, () -> itemRepository.saveAll(folders));
        get(items(s), BOB).andExpect(jsonPath("$", hasSize(300)));
    }

    @Test
    @DisplayName("FILE-04e: 파일을 올린 이전 담당자가 담당에서 벗어나 탈퇴하면 이름과 uploaderWithdrawn=true")
    void withdrawnUploader() throws Exception {
        uploadOne(null, "a.txt", "x");
        as(alice, () -> spaceService.forceReassign(alice, s,
                new com.back.space.api.SpaceDto.ForcedReassignmentReq(dave, "사유")));
        post("/api/v1/users/me/withdrawal", BOB, body("\"password\":\"" + PASSWORD + "\""))
                .andExpect(status().isNoContent());

        get(items(s), DAVE)
                .andExpect(jsonPath("$[0].uploaderName").value("밥"))
                .andExpect(jsonPath("$[0].uploaderWithdrawn").value(true));
    }

    @Test
    @DisplayName("휴지통 폴더나 파일 id를 folderId로 주면 404 / 400")
    void listInvalidFolder() throws Exception {
        long d = folder(null, "D");
        long f = uploadOne(null, "f.txt", "x");
        trash(d);
        get(items(s) + "?folderId=" + d, BOB).andExpect(status().isNotFound());
        get(items(s) + "?folderId=" + f, BOB).andExpect(status().isBadRequest());
        get(items(s) + "/" + d, BOB).andExpect(status().isNotFound());
    }
}
