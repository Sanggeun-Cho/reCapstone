package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.file.domain.DriveItem;
import com.back.file.event.FileUploaded;
import com.back.support.FileTestSupport;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 업로드 (정책 8.2, 8.3, 8.4 제1항). 이슈 #13 테스트 명세의 "업로드·목록". */
class FileUploadApiTest extends FileTestSupport {

    @Test
    @DisplayName("FILE-01: 파일 2개를 최상위에 올리면 DB 2행, 저장소 객체 2개(키 spaces/{S}/files/{uuid}), FileUploaded 2건")
    void uploadTwo() throws Exception {
        upload(BOB, s, file("a.txt", "hello"), file("b.pdf", PDF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created", hasSize(2)))
                .andExpect(jsonPath("$.created[*].name", contains("a.txt", "b.pdf")))
                .andExpect(jsonPath("$.created[*].replaced", contains(false, false)))
                .andExpect(jsonPath("$.skipped", hasSize(0)))
                .andExpect(jsonPath("$.failed", hasSize(0)));

        List<DriveItem> rows = itemRepository.findAll();
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.getParentId()).isNull();
            assertThat(row.getDepth()).isEqualTo(1);
            assertThat(row.getUploaderId()).isEqualTo(bob);
            assertThat(row.getStorageKey()).matches("spaces/" + s + "/files/[0-9a-f-]{36}");
            assertThat(objectExists(row.getStorageKey())).isTrue();
        });
        DriveItem pdf = rows.stream().filter(r -> r.getName().equals("b.pdf")).findFirst().orElseThrow();
        assertThat(pdf.getSize()).isEqualTo(PDF.length);
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getDetectedType()).isEqualTo("application/pdf");
        assertThat(pdf.isInlineViewable()).isTrue();
        assertThat(events.stream(FileUploaded.class).map(FileUploaded::fileId))
                .containsExactlyInAnyOrderElementsOf(rows.stream().map(DriveItem::getId).toList());

        String url = downloadUrl(BOB, s, pdf.getId(), "ATTACHMENT");
        assertThat(fetch(url).body()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("FILE-01b: folderId를 주면 그 폴더 아래에 저장된다")
    void uploadIntoFolder() throws Exception {
        long d = folder(null, "D");
        upload(BOB, s, List.of(file("a.txt", "x")), "folderId=" + d).andExpect(status().isCreated());
        DriveItem row = itemRepository.findAll().stream().filter(DriveItem::isFile).findFirst().orElseThrow();
        assertThat(row.getParentId()).isEqualTo(d);
        assertThat(row.getDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("FILE-01c: 휴지통 폴더 / 다른 스페이스 폴더 / 파일 id를 folderId로 주면 404 / 404 / 400 (요청 전체 거부)")
    void invalidFolder() throws Exception {
        long trashed = folder(null, "T");
        trash(trashed);
        assign(alice, s2, bob);
        long other = as(bob, () -> fileService.createFolder(bob, s2,
                new com.back.file.api.FileDto.FolderReq(null, "O")).id());
        long aFile = uploadOne(null, "f.txt", "x");

        upload(BOB, s, List.of(file("a.txt", "x")), "folderId=" + trashed).andExpect(status().isNotFound());
        upload(BOB, s, List.of(file("a.txt", "x")), "folderId=" + other).andExpect(status().isNotFound());
        upload(BOB, s, List.of(file("a.txt", "x")), "folderId=" + aFile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOVE_TARGET"));
        assertThat(objectCount(s)).isEqualTo(1);
    }

    @Test
    @DisplayName("FILE-02: 0바이트 파일은 건너뛰고 나머지만 저장, 응답에 건너뛴 이름")
    void skipEmpty() throws Exception {
        upload(BOB, s, file("empty.txt", new byte[0]), file("a.txt", "x"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.skipped", contains("empty.txt")));
        assertThat(itemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("FILE-02b: 0바이트 파일만 올리면 201, 저장 0건, 실패 0건")
    void onlyEmpty() throws Exception {
        upload(BOB, s, file("empty.txt", new byte[0]))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created", hasSize(0)))
                .andExpect(jsonPath("$.failed", hasSize(0)))
                .andExpect(jsonPath("$.skipped", contains("empty.txt")));
    }

    @Test
    @DisplayName("FILE-03: 확장자 없는 README는 저장되고 octet-stream, 내려받기 전용")
    void noExtension() throws Exception {
        long id = uploadOne(null, "README", "read me");
        DriveItem row = item(id);
        assertThat(row.getContentType()).isEqualTo("application/octet-stream");
        assertThat(row.isInlineViewable()).isFalse();
    }

    @Test
    @DisplayName("NFR-01: 200MB 정확히는 저장, 200MB+1바이트는 413 FILE_TOO_LARGE (요청 전체)")
    void fileSizeLimit() throws Exception {
        long limit = 200L * 1024 * 1024;
        upload(BOB, s, sized("over.bin", limit + 1), file("a.txt", "x"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(itemRepository.count()).isZero();

        upload(BOB, s, sized("exact.bin", limit)).andExpect(status().isCreated());
        assertThat(item(itemRepository.findAll().getFirst().getId()).getSize()).isEqualTo(limit);
    }

    @Test
    @DisplayName("NFR-01b: 20개는 저장, 21개는 400 TOO_MANY_FILES (저장 없음)")
    void fileCountLimit() throws Exception {
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            files.add(file("f" + i + ".txt", "x"));
        }
        upload(BOB, s, files).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();

        upload(BOB, s, files.subList(0, 20)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.created", hasSize(20)));
    }

    @Test
    @DisplayName("NFR-01c: 150MB × 4 (요청 600MB)는 413, 저장 없음")
    void requestSizeLimit() throws Exception {
        long size = 150L * 1024 * 1024;
        upload(BOB, s, sized("1.bin", size), sized("2.bin", size), sized("3.bin", size), sized("4.bin", size))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("NFR-02: 한글 파일명이 업로드·목록·내려받기에서 깨지지 않는다")
    void koreanName() throws Exception {
        long id = uploadOne(s, null, "인수인계 자료.pdf", PDF);
        get(items(s), BOB).andExpect(jsonPath("$[0].name").value("인수인계 자료.pdf"));
        var response = fetch(downloadUrl(BOB, s, id, "ATTACHMENT"));
        assertThat(response.headers().firstValue("Content-Disposition")).hasValue(
                "attachment; filename*=UTF-8''%EC%9D%B8%EC%88%98%EC%9D%B8%EA%B3%84%20%EC%9E%90%EB%A3%8C.pdf");
    }

    @Test
    @DisplayName("FILE-15: 보고서.pdf가 있을 때 보고서.PDF, 보고서.pdf를 한 요청으로 올리면 (1), (2)가 붙는다")
    void caseInsensitiveNumbering() throws Exception {
        uploadOne(null, "보고서.pdf", "v0");
        upload(BOB, s, file("보고서.PDF", "v1"), file("보고서.pdf", "v2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[*].name", contains("보고서 (1).PDF", "보고서 (2).pdf")));
    }

    @Test
    @DisplayName("FILE-17: NFD 한글.txt 다음에 NFC 한글.txt를 올리면 한글 (1).txt. 저장된 이름은 모두 NFC")
    void nfdName() throws Exception {
        String nfd = Normalizer.normalize("한글.txt", Normalizer.Form.NFD);
        uploadOne(null, nfd, "a");
        upload(BOB, s, file("한글.txt", "b"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].name").value("한글 (1).txt"));
        assertThat(itemRepository.findAll()).extracting(DriveItem::getName)
                .containsExactlyInAnyOrder("한글.txt", "한글 (1).txt")
                .allSatisfy(name -> assertThat(Normalizer.isNormalized(name, Normalizer.Form.NFC)).isTrue());
    }

    @Test
    @DisplayName("FILE-19: 기록 트랜잭션에서 DB 오류가 나면 5xx, DB 0행, 올린 객체도 지운다")
    void recordFailureDeletesObjects() throws Exception {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_drive_item_insert() RETURNS trigger LANGUAGE plpgsql AS
                $$ BEGIN RAISE EXCEPTION 'insert failed (test)'; END; $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_drive_item_insert BEFORE INSERT ON drive_item
                FOR EACH ROW EXECUTE FUNCTION fail_drive_item_insert()
                """);
        try {
            upload(BOB, s, file("a.txt", "x"), file("b.txt", "y")).andExpect(status().is5xxServerError());
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_drive_item_insert ON drive_item");
            jdbcTemplate.execute("DROP FUNCTION fail_drive_item_insert()");
        }
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("FILE-20: 저장소가 내려가 있으면 503 STORAGE_UNAVAILABLE, failed 2건, DB 0행")
    void storageDown() throws Exception {
        fileStorage.down();
        upload(BOB, s, file("a.txt", "x"), file("b.txt", "y"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.failed", hasSize(2)))
                .andExpect(jsonPath("$.failed[*].code", contains("STORAGE_UNAVAILABLE", "STORAGE_UNAVAILABLE")));
        assertThat(itemRepository.count()).isZero();
    }

    @Test
    @DisplayName("FILE-21: alice(OWNER)와 carol은 403, dave는 404")
    void uploadPermission() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        upload(CAROL, s, file("a.txt", "x")).andExpect(status().isForbidden());
        handoverStatus.reset();
        upload(ALICE, s, file("a.txt", "x")).andExpect(status().isForbidden());
        upload(DAVE, s, file("a.txt", "x")).andExpect(status().isNotFound());
        upload(MALLORY, s, file("a.txt", "x")).andExpect(status().isNotFound());
        upload(ANON, s, file("a.txt", "x")).andExpect(status().isUnauthorized());
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("FILE-31: 정상 a.txt + con.txt + 정상 b.txt는 201, 2건 저장, con.txt는 INVALID_ITEM_NAME으로 실패하고 객체 없음")
    void partialSuccess() throws Exception {
        upload(BOB, s, file("a.txt", "a"), file("con.txt", "c"), file("b.txt", "b"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[*].name", contains("a.txt", "b.txt")))
                .andExpect(jsonPath("$.failed", hasSize(1)))
                .andExpect(jsonPath("$.failed[0].name").value("con.txt"))
                .andExpect(jsonPath("$.failed[0].code").value("INVALID_ITEM_NAME"));
        assertThat(objectCount(s)).isEqualTo(2);
    }

    @Test
    @DisplayName("FILE-31b: con.txt, nul.pdf만 올리면 400 INVALID_ITEM_NAME, failed 2건, 저장 없음")
    void allInvalid() throws Exception {
        upload(BOB, s, file("con.txt", "c"), file("nul.pdf", "n"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ITEM_NAME"))
                .andExpect(jsonPath("$.failed[*].name", containsInAnyOrder("con.txt", "nul.pdf")));
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("FILE-31c: 두 번째 파일만 전송이 끊기면 201, 2건 저장, 1건 STORAGE_UNAVAILABLE. 실패 파일의 객체·멀티파트 조각 없음")
    void oneTransferFails() throws Exception {
        fileStorage.failPutAt(2);
        byte[] big = "x".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        upload(BOB, s, file("a.txt", big), file("b.txt", big), file("c.txt", big))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[*].name", contains("a.txt", "c.txt")))
                .andExpect(jsonPath("$.failed[0].name").value("b.txt"))
                .andExpect(jsonPath("$.failed[0].code").value("STORAGE_UNAVAILABLE"));
        assertThat(objectCount(s)).isEqualTo(2);
    }
}
