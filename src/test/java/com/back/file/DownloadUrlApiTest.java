package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.FileTestSupport;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 내려받기 주소 발급 (정책 8.1, 8.6). */
class DownloadUrlApiTest extends FileTestSupport {

    private String url(long itemId) {
        return "/api/v1/spaces/" + s + "/files/" + itemId + "/download-url";
    }

    @Test
    @DisplayName("FILE-05: ATTACHMENT 주소는 5분 유효, 받으면 attachment·UTF-8 파일명, 내용 일치")
    void attachment() throws Exception {
        long id = uploadOne(null, "a.docx", "docx body");
        post(url(id), BOB, body("\"disposition\":\"ATTACHMENT\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(5)).toString()));
        HttpResponse<byte[]> response = fetch(downloadUrl(BOB, s, id, "ATTACHMENT"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition")).hasValue("attachment; filename*=UTF-8''a.docx");
        assertThat(new String(response.body())).isEqualTo("docx body");
    }

    @Test
    @DisplayName("본문을 생략하면 ATTACHMENT")
    void defaultAttachment() throws Exception {
        long id = uploadOne(null, "a.txt", "x");
        String body = post(url(id), BOB, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String link = com.jayway.jsonpath.JsonPath.read(body, "$.url");
        assertThat(fetch(link).headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
    }

    @Test
    @DisplayName("FILE-06·07b: docx와 svg는 INLINE 요청 시 400 INLINE_NOT_ALLOWED")
    void inlineNotAllowed() throws Exception {
        long docx = uploadOne(null, "a.docx", "x");
        long svg = uploadOne(null, "a.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>");
        post(url(docx), BOB, body("\"disposition\":\"INLINE\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INLINE_NOT_ALLOWED"));
        post(url(svg), BOB, body("\"disposition\":\"INLINE\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INLINE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("FILE-07: 실제 PNG a.png를 INLINE으로 받으면 inline, image/png")
    void inlinePng() throws Exception {
        long id = uploadOne(s, null, "a.png", PNG);
        HttpResponse<byte[]> response = fetch(downloadUrl(BOB, s, id, "INLINE"));
        assertThat(response.headers().firstValue("Content-Disposition")).hasValue("inline; filename*=UTF-8''a.png");
        assertThat(response.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(response.body()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("FILE-07a: HTML 내용의 a.png는 업로드 201(inlineViewable=false), INLINE 400, ATTACHMENT 200")
    void htmlDisguisedAsPng() throws Exception {
        upload(BOB, s, file("a.png", HTML)).andExpect(status().isCreated());
        long id = itemRepository.findAll().getFirst().getId();
        get(items(s), BOB).andExpect(jsonPath("$[0].inlineViewable").value(false));
        post(url(id), BOB, body("\"disposition\":\"INLINE\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INLINE_NOT_ALLOWED"));
        post(url(id), BOB, body("\"disposition\":\"ATTACHMENT\"")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FILE-26: 만료된 주소는 저장소가 403으로 거부한다 (짧은 유효 기간을 주입)")
    void expired() throws Exception {
        long id = uploadOne(null, "a.txt", "x");
        String link = fileStorage.real().presignGet(storageKey(id), "attachment", "text/plain", Duration.ofSeconds(1))
                .toString();
        Instant deadline = Instant.now().plusSeconds(3);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
        }
        assertThat(fetch(link).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("FILE-27: 폴더 id는 400, 휴지통 파일은 404")
    void invalidTargets() throws Exception {
        long d = folder(null, "D");
        long f = uploadOne(null, "f.txt", "x");
        trash(f);
        post(url(d), BOB, null).andExpect(status().isBadRequest());
        post(url(f), BOB, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FILE-28: alice·bob·carol(인수자) 200, dave·mallory 404, 비로그인 401. 인계 중에도 읽기는 허용")
    void permission() throws Exception {
        long f = uploadOne(null, "f.txt", "x");
        handoverStatus.start(s, g, bob, carol);
        post(url(f), ALICE, null).andExpect(status().isOk());
        post(url(f), BOB, null).andExpect(status().isOk());
        post(url(f), CAROL, null).andExpect(status().isOk());
        post(url(f), DAVE, null).andExpect(status().isNotFound());
        post(url(f), MALLORY, null).andExpect(status().isNotFound());
        post(url(f), ANON, null).andExpect(status().isUnauthorized());
    }
}
