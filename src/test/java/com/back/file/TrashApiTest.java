package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.file.event.FileRemoved;
import com.back.space.api.SpaceDto;
import com.back.support.FileTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 삭제와 휴지통 (정책 8.5). */
class TrashApiTest extends FileTestSupport {

    private org.springframework.test.web.servlet.ResultActions restore(String email, long itemId) throws Exception {
        return post(trashUrl(s) + "/" + itemId + "/restore", email, null);
    }

    @Test
    @DisplayName("FILE-08: 파일을 삭제하면 movedCount 1, 목록에서 사라지고 휴지통에 나타난다")
    void trashFile() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        delete(items(s) + "/" + f, BOB).andExpect(status().isOk()).andExpect(jsonPath("$.movedCount").value(1));
        get(items(s), BOB).andExpect(jsonPath("$", hasSize(0)));
        get(trashUrl(s), BOB).andExpect(jsonPath("$[*].id", contains((int) f)));
    }

    @Test
    @DisplayName("FILE-13: 하위 항목 4개가 있는 폴더 D를 삭제하면 movedCount 5, trashed_at은 D에만")
    void trashFolder() throws Exception {
        long d = folder(null, "D");
        uploadOne(d, "1.txt", "1");
        uploadOne(d, "2.txt", "2");
        long sub = folder(d, "sub");
        long inner = uploadOne(sub, "3.txt", "3");
        delete(items(s) + "/" + d, BOB).andExpect(jsonPath("$.movedCount").value(5));
        assertThat(item(d).isTrashed()).isTrue();
        assertThat(item(sub).isTrashed()).isFalse();
        assertThat(item(inner).isTrashed()).isFalse();
        get(items(s), BOB).andExpect(jsonPath("$", hasSize(0)));
        get(items(s) + "/" + inner, BOB).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TRASH-01: f와 D(하위 2개)를 지우면 휴지통은 2건, 삭제 최신순, 원래 경로와 영구 삭제 예정 시각")
    void listTrash() throws Exception {
        long p = folder(null, "P");
        long f = uploadOne(p, "f.txt", "x");
        long d = folder(null, "D");
        uploadOne(d, "1.txt", "1");
        uploadOne(d, "2.txt", "2");
        trash(f);
        clock.advance(Duration.ofSeconds(1));
        trash(d);

        get(trashUrl(s), BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("D", "f.txt")))
                .andExpect(jsonPath("$[*].originalPath", contains("/", "/P")))
                .andExpect(jsonPath("$[0].trashedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$[0].purgeAt").value(clock.instant().plus(Duration.ofDays(30)).toString()));
    }

    @Test
    @DisplayName("TRASH-02: 폴더 P의 f를 복구하면 P로 돌아온다")
    void restoreToOriginal() throws Exception {
        long p = folder(null, "P");
        long f = uploadOne(p, "f.txt", "x");
        trash(f);
        restore(BOB, f).andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(p))
                .andExpect(jsonPath("$.name").value("f.txt"));
        get(items(s) + "?folderId=" + p, BOB).andExpect(jsonPath("$[*].name", contains("f.txt")));
        get(trashUrl(s), BOB).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("TRASH-03: f를 지우고 P도 지운 뒤 f를 복구하면 최상위로. P는 휴지통에 남는다")
    void restoreToRootWhenParentTrashed() throws Exception {
        long p = folder(null, "P");
        long f = uploadOne(p, "f.txt", "x");
        trash(f);
        trash(p);
        restore(BOB, f).andExpect(jsonPath("$.parentId").doesNotExist());
        assertThat(item(f).getParentId()).isNull();
        assertThat(item(p).isTrashed()).isTrue();
    }

    @Test
    @DisplayName("TRASH-04: a.pdf를 지우고 같은 이름을 올린 뒤 복구하면 a (1).pdf")
    void restoreRenumbers() throws Exception {
        long f = uploadOne(null, "a.pdf", "old");
        trash(f);
        uploadOne(null, "a.pdf", "new");
        restore(BOB, f).andExpect(jsonPath("$.name").value("a (1).pdf"));
    }

    @Test
    @DisplayName("TRASH-05: 폴더 D를 복구하면 하위 4개도 목록에 다시 보인다")
    void restoreFolder() throws Exception {
        long d = folder(null, "D");
        uploadOne(d, "1.txt", "1");
        uploadOne(d, "2.txt", "2");
        long sub = folder(d, "sub");
        uploadOne(sub, "3.txt", "3");
        trash(d);
        restore(BOB, d).andExpect(status().isOk());
        get(items(s) + "?folderId=" + d, BOB).andExpect(jsonPath("$[*].name", contains("sub", "1.txt", "2.txt")));
        get(items(s) + "?folderId=" + sub, BOB).andExpect(jsonPath("$[*].name", contains("3.txt")));
    }

    @Test
    @DisplayName("TRASH-06: D의 하위 g를 먼저 지우고 D를 지운 뒤 D를 복구해도 g는 휴지통에 남는다")
    void separatelyTrashedChildStays() throws Exception {
        long d = folder(null, "D");
        long g2 = uploadOne(d, "g.txt", "g");
        uploadOne(d, "h.txt", "h");
        trash(g2);
        trash(d);
        restore(BOB, d).andExpect(status().isOk());
        get(items(s) + "?folderId=" + d, BOB).andExpect(jsonPath("$[*].name", contains("h.txt")));
        get(trashUrl(s), BOB).andExpect(jsonPath("$[*].id", contains((int) g2)));
    }

    @Test
    @DisplayName("TRASH-07: 휴지통의 f를 영구 삭제하면 204, DB 행·저장소 객체·대기열 없음, FileRemoved")
    void purge() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        String key = storageKey(f);
        trash(f);
        delete(trashUrl(s) + "/" + f, BOB).andExpect(status().isNoContent());
        assertThat(itemRepository.findById(f)).isEmpty();
        assertThat(objectExists(key)).isFalse();
        assertThat(count("SELECT count(*) FROM storage_deletion")).isZero();
        assertThat(events.stream(FileRemoved.class)).containsExactly(new FileRemoved(s, f));
    }

    @Test
    @DisplayName("TRASH-07b: 휴지통의 D(하위 파일 3개)를 영구 삭제하면 하위까지 DB 행·객체가 지워지고 FileRemoved 3건")
    void purgeFolder() throws Exception {
        long d = folder(null, "D");
        long sub = folder(d, "sub");
        List<Long> files = List.of(uploadOne(d, "1.txt", "1"), uploadOne(d, "2.txt", "2"), uploadOne(sub, "3.txt", "3"));
        trash(d);
        delete(trashUrl(s) + "/" + d, BOB).andExpect(status().isNoContent());
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
        assertThat(events.stream(FileRemoved.class).map(FileRemoved::fileId)).containsExactlyInAnyOrderElementsOf(files);
    }

    @Test
    @DisplayName("TRASH-08: 휴지통 3건을 비우면 204, 휴지통 0건, 객체 삭제")
    void empty() throws Exception {
        trash(uploadOne(null, "1.txt", "1"));
        trash(uploadOne(null, "2.txt", "2"));
        trash(folder(null, "D"));
        long kept = uploadOne(null, "kept.txt", "k");
        delete(trashUrl(s), BOB).andExpect(status().isNoContent());
        get(trashUrl(s), BOB).andExpect(jsonPath("$", hasSize(0)));
        assertThat(objectCount(s)).isEqualTo(1);
        assertThat(item(kept).isTrashed()).isFalse();
    }

    @Test
    @DisplayName("TRASH-09: 삭제 후 30일 - 1초에는 남고, 정확히 30일이면 정리 작업이 영구 삭제한다")
    void expiry() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        trash(f);
        clock.advance(Duration.ofDays(30).minusSeconds(1));
        assertThat(trashService.purgeExpired()).isZero();
        assertThat(itemRepository.findById(f)).isPresent();
        clock.advance(Duration.ofSeconds(1));
        assertThat(trashService.purgeExpired()).isEqualTo(1);
        assertThat(itemRepository.findById(f)).isEmpty();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("TRASH-10: alice·carol(인수자)는 403, dave·mallory는 404 (조회·복구·영구 삭제·비우기)")
    void permission() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        trash(f);
        for (String email : List.of(ALICE, DAVE, MALLORY)) {
            int expected = email.equals(ALICE) ? 403 : 404;
            get(trashUrl(s), email).andExpect(status().is(expected));
            restore(email, f).andExpect(status().is(expected));
            delete(trashUrl(s) + "/" + f, email).andExpect(status().is(expected));
            delete(trashUrl(s), email).andExpect(status().is(expected));
        }
        // 인수자는 인계 중에만 있으므로 권한 검사(403)가 인계 잠금(409)보다 먼저다
        handoverStatus.start(s, g, bob, carol);
        get(trashUrl(s), CAROL).andExpect(status().isForbidden());
        restore(CAROL, f).andExpect(status().isForbidden());
        assertThat(item(f).isTrashed()).isTrue();
    }

    @Test
    @DisplayName("TRASH-11: 휴지통이 아닌 항목, 폴더와 함께 들어간 하위 항목은 복구·영구 삭제 404")
    void notTrashTop() throws Exception {
        long active = uploadOne(null, "a.txt", "a");
        long d = folder(null, "D");
        long child = uploadOne(d, "c.txt", "c");
        trash(d);
        restore(BOB, active).andExpect(status().isNotFound());
        delete(trashUrl(s) + "/" + active, BOB).andExpect(status().isNotFound());
        restore(BOB, child).andExpect(status().isNotFound());
        delete(trashUrl(s) + "/" + child, BOB).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TRASH-12: 휴지통이 비었는지 (S5 인계 시작 조건)")
    void isEmpty() throws Exception {
        assertThat(trashService.isEmpty(s)).isTrue();
        trash(uploadOne(null, "f.txt", "x"));
        assertThat(trashService.isEmpty(s)).isFalse();
    }

    @Test
    @DisplayName("TRASH-14: bob이 지운 뒤 긴급 재지정으로 담당자가 dave가 되면 dave가 휴지통을 이어받고 bob은 404")
    void newAssigneeInheritsTrash() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        trash(f);
        as(alice, () -> spaceService.forceReassign(alice, s, new SpaceDto.ForcedReassignmentReq(dave, "긴급")));
        get(trashUrl(s), DAVE).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains((int) f)));
        restore(DAVE, f).andExpect(status().isOk());
        get(trashUrl(s), BOB).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TRASH-15: 원래 폴더로 돌리면 10단계를 넘으면 최상위로 복구한다 (응답 parentId null)")
    void restoreToRootWhenTooDeep() throws Exception {
        long p = folderChain(null, 5, "p");
        long t = folder(p, "T");
        folderChain(t, 3, "t");
        trash(t);
        long six = folderChain(null, 6, "s");
        // 휴지통 하위는 세지 않으므로 P(깊이 5)를 깊이 6 폴더 아래로 옮길 수 있다
        as(bob, () -> fileService.move(bob, s, p, new com.back.file.api.FileDto.MoveReq(six)));
        assertThat(item(p).getDepth()).isEqualTo(7);

        restore(BOB, t).andExpect(status().isOk()).andExpect(jsonPath("$.parentId").doesNotExist());
        assertThat(item(t).getDepth()).isEqualTo(1);
    }

    @Test
    @DisplayName("TRASH-15b: 넘치지 않으면 원래 폴더 P로 복구하고 하위 깊이도 다시 계산한다")
    void restoreDepthRecalculated() throws Exception {
        long p = folderChain(null, 3, "p");
        long t = folder(p, "T");
        long leaf = folderChain(t, 2, "t");
        trash(t);
        restore(BOB, t).andExpect(jsonPath("$.parentId").value(p));
        assertThat(item(t).getDepth()).isEqualTo(4);
        assertThat(item(leaf).getDepth()).isEqualTo(6);
    }

    @Test
    @DisplayName("휴지통 목록의 이름 순서와 무관하게 여러 항목을 함께 보여 준다")
    void listsBothTypes() throws Exception {
        trash(folder(null, "D"));
        trash(uploadOne(null, "f.txt", "x"));
        get(trashUrl(s), BOB).andExpect(jsonPath("$[*].type", containsInAnyOrder("FOLDER", "FILE")));
    }
}
