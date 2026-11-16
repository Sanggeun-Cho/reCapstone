package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.file.domain.DriveItem;
import com.back.file.event.FileUploaded;
import com.back.support.FileTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 같은 이름의 파일을 올릴 때 (정책 8.4). */
class FileReplaceApiTest extends FileTestSupport {

    private static final String REPLACE = "onConflict=REPLACE";

    private List<DriveItem> trashed() {
        return itemRepository.findBySpaceIdAndTrashedAtIsNotNull(s);
    }

    @Test
    @DisplayName("FILE-29: REPLACE로 a.pdf를 올리면 id 유지, 내용·크기·업로더가 v2, 휴지통에 v1, FileUploaded(X) 1건")
    void replace() throws Exception {
        long p = folder(null, "P");
        long x = uploadOne(p, "a.pdf", "v1");
        String v1Key = storageKey(x);
        events.clear();

        upload(BOB, s, List.of(file("a.pdf", PDF)), "folderId=" + p, REPLACE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].id").value(x))
                .andExpect(jsonPath("$.created[0].name").value("a.pdf"))
                .andExpect(jsonPath("$.created[0].replaced").value(true));

        DriveItem current = item(x);
        assertThat(current.getSize()).isEqualTo(PDF.length);
        assertThat(current.getDetectedType()).isEqualTo("application/pdf");
        assertThat(current.isInlineViewable()).isTrue();
        assertThat(current.getStorageKey()).isNotEqualTo(v1Key);
        assertThat(fetch(downloadUrl(BOB, s, x, "ATTACHMENT")).body()).isEqualTo(PDF);

        assertThat(trashed()).singleElement().satisfies(previous -> {
            assertThat(previous.getName()).isEqualTo("a.pdf");
            assertThat(previous.getStorageKey()).isEqualTo(v1Key);
            assertThat(previous.getOriginalParentId()).isEqualTo(p);
        });
        get(trashUrl(s), BOB).andExpect(jsonPath("$[0].originalPath").value("/P"));
        assertThat(events.stream(FileUploaded.class)).containsExactly(new FileUploaded(s, x));
    }

    @Test
    @DisplayName("FILE-29b: 덮어쓴 뒤 휴지통의 v1을 복구하면 a (1).pdf로 P에 돌아오고 X는 그대로")
    void restorePrevious() throws Exception {
        long p = folder(null, "P");
        long x = uploadOne(p, "a.pdf", "v1");
        upload(BOB, s, List.of(file("a.pdf", "v2")), "folderId=" + p, REPLACE).andExpect(status().isCreated());
        long v1 = trashed().getFirst().getId();

        post(trashUrl(s) + "/" + v1 + "/restore", BOB, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("a (1).pdf"))
                .andExpect(jsonPath("$.parentId").value(p));
        assertThat(fetch(downloadUrl(BOB, s, v1, "ATTACHMENT")).body())
                .isEqualTo("v1".getBytes(StandardCharsets.UTF_8));
        assertThat(item(x).getName()).isEqualTo("a.pdf");
    }

    @Test
    @DisplayName("FILE-29c: 같은 이름이 폴더면 그 파일만 NAME_CONFLICT로 실패하고 나머지는 덮어쓴다")
    void replaceFolderConflict() throws Exception {
        long p = folder(null, "P");
        long folderA = folder(p, "a.pdf");
        long b = uploadOne(p, "b.txt", "old");

        upload(BOB, s, List.of(file("a.pdf", "x"), file("b.txt", "new")), "folderId=" + p, REPLACE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].id").value(b))
                .andExpect(jsonPath("$.created[0].replaced").value(true))
                .andExpect(jsonPath("$.failed[0].name").value("a.pdf"))
                .andExpect(jsonPath("$.failed[0].code").value("NAME_CONFLICT"));
        assertThat(item(folderA).isFolder()).isTrue();
        // 덮어쓴 b의 새 내용과 휴지통의 이전 내용만 남는다. 실패한 a.pdf의 객체는 지운다
        assertThat(objectCount(s)).isEqualTo(2);
    }

    @Test
    @DisplayName("FILE-29d: 같은 이름이 휴지통에만 있으면 새 항목을 만든다 (replaced:false)")
    void replaceOnlyTrashed() throws Exception {
        long old = uploadOne(null, "a.pdf", "v1");
        trash(old);
        upload(BOB, s, List.of(file("a.pdf", "v2")), REPLACE)
                .andExpect(jsonPath("$.created[0].replaced").value(false));
        assertThat(item(old).isTrashed()).isTrue();
        assertThat(trashed()).hasSize(1);
    }

    @Test
    @DisplayName("FILE-29e: A.PDF가 있을 때 REPLACE로 a.pdf를 올리면 대소문자 무시로 덮어쓰고 이름은 A.PDF 유지")
    void replaceCaseInsensitive() throws Exception {
        long x = uploadOne(null, "A.PDF", "v1");
        upload(BOB, s, List.of(file("a.pdf", "v2")), REPLACE)
                .andExpect(jsonPath("$.created[0].id").value(x))
                .andExpect(jsonPath("$.created[0].name").value("A.PDF"));
    }

    @Test
    @DisplayName("FILE-29f: 한 요청에 REPLACE로 n.txt 두 개(기존 없음)는 n.txt, n (1).txt")
    void sameNameInRequest() throws Exception {
        upload(BOB, s, List.of(file("n.txt", "1"), file("n.txt", "2")), REPLACE)
                .andExpect(jsonPath("$.created[*].name", contains("n.txt", "n (1).txt")))
                .andExpect(jsonPath("$.created[*].replaced", contains(false, false)));
        assertThat(trashed()).isEmpty();
    }

    @Test
    @DisplayName("FILE-29f2: REPLACE는 같은 요청이 방금 만든 파일을 덮어쓰지 않는다 (a.pdf, a.pdf, a (1).pdf)")
    void replaceDoesNotOverwriteOwnUpload() throws Exception {
        long x = uploadOne(null, "a.pdf", "v0");
        upload(BOB, s, List.of(file("a.pdf", "1"), file("a.pdf", "2"), file("a (1).pdf", "3")), REPLACE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[*].name", contains("a.pdf", "a (1).pdf", "a (1) (1).pdf")))
                .andExpect(jsonPath("$.created[*].replaced", contains(true, false, false)))
                .andExpect(jsonPath("$.created[0].id").value(x));
        // 휴지통에는 요청 전에 있던 a.pdf의 이전 내용만 들어간다
        assertThat(trashed()).singleElement().satisfies(previous -> assertThat(previous.getName()).isEqualTo("a.pdf"));
        assertThat(itemRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("FILE-29g: onConflict를 생략하면 RENAME (a (1).pdf)")
    void defaultRename() throws Exception {
        uploadOne(null, "a.pdf", "v1");
        upload(BOB, s, file("a.pdf", "v2")).andExpect(jsonPath("$.created[0].name").value("a (1).pdf"));
        assertThat(trashed()).isEmpty();
    }

    @Test
    @DisplayName("FILE-29h: 인계 진행 중에는 REPLACE 업로드도 409, 변화 없음")
    void replaceDuringHandover() throws Exception {
        long x = uploadOne(null, "a.pdf", "v1");
        String key = storageKey(x);
        handoverStatus.start(s, g, bob, carol);
        upload(BOB, s, List.of(file("a.pdf", "v2")), REPLACE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
        assertThat(storageKey(x)).isEqualTo(key);
        assertThat(objectCount(s)).isEqualTo(1);
    }

    @Test
    @DisplayName("FILE-29i: 덮어쓰기 전에 받은 주소는 만료 전까지 이전 내용(v1)을 준다")
    void oldUrlServesPreviousContent() throws Exception {
        long x = uploadOne(null, "a.txt", "v1");
        String oldUrl = downloadUrl(BOB, s, x, "ATTACHMENT");
        upload(BOB, s, List.of(file("a.txt", "v2")), REPLACE).andExpect(status().isCreated());
        assertThat(fetch(oldUrl).body()).isEqualTo("v1".getBytes(StandardCharsets.UTF_8));
    }
}
