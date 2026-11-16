package com.back.file.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentTypeResolverTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};
    static final byte[] HTML = "<!DOCTYPE html><html><body>x</body></html>".getBytes(StandardCharsets.UTF_8);
    static final byte[] TEXT = "just some text\n".getBytes(StandardCharsets.UTF_8);

    private static boolean inline(String name, byte[] content) {
        return ContentTypeResolver.inlineViewable(name, ContentTypeResolver.detect(content));
    }

    @Test
    @DisplayName("TYPE-U1: 실제 PNG a.png, 실제 PDF b.PDF, 실제 JPEG c.jpg는 바로 열 수 있다")
    void matchingTypes() {
        assertThat(ContentTypeResolver.detect(PNG)).isEqualTo("image/png");
        assertThat(ContentTypeResolver.detect(PDF)).isEqualTo("application/pdf");
        assertThat(ContentTypeResolver.detect(JPEG)).isEqualTo("image/jpeg");
        assertThat(inline("a.png", PNG)).isTrue();
        assertThat(inline("b.PDF", PDF)).isTrue();
        assertThat(inline("c.jpg", JPEG)).isTrue();
        assertThat(inline("c.jpeg", JPEG)).isTrue();
        assertThat(ContentTypeResolver.byName("a.png")).isEqualTo("image/png");
        assertThat(ContentTypeResolver.byName("b.PDF")).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("TYPE-U2: 확장자와 실제 형식이 다르면 내려받기 전용 (HTML 내용의 a.png, 텍스트 b.pdf, PNG 내용의 c.pdf)")
    void mismatchingTypes() {
        assertThat(inline("a.png", HTML)).isFalse();
        assertThat(inline("b.pdf", TEXT)).isFalse();
        assertThat(inline("c.pdf", PNG)).isFalse();
    }

    @Test
    @DisplayName("TYPE-U3: svg, html, docx, 확장자 없음은 내려받기 전용. 표시용 형식은 확장자 기준, 없으면 octet-stream")
    void downloadOnly() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThat(inline("c.svg", svg)).isFalse();
        assertThat(inline("d.html", HTML)).isFalse();
        assertThat(inline("e.docx", TEXT)).isFalse();
        assertThat(inline("f", PNG)).isFalse();
        assertThat(ContentTypeResolver.byName("e.docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(ContentTypeResolver.byName("f")).isEqualTo(ContentTypeResolver.OCTET_STREAM);
        assertThat(ContentTypeResolver.byName(".env")).isEqualTo(ContentTypeResolver.OCTET_STREAM);
    }

    @Test
    @DisplayName("TYPE-U4: docx·zip·txt도 실제 형식을 판별해 저장한다 (값은 Tika 결과 그대로)")
    void detectsAnyType() throws IOException {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            out.putNextEntry(new ZipEntry("[Content_Types].xml"));
            out.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertThat(ContentTypeResolver.detect(zip.toByteArray())).isNotBlank()
                .isNotEqualTo(ContentTypeResolver.OCTET_STREAM);
        assertThat(ContentTypeResolver.detect(TEXT)).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("TYPE-U5: 저장된 실제 형식 image/png로 이름을 바꿔 다시 판단 (x.txt / x.png / x.jpg)")
    void rejudgeOnRename() {
        assertThat(ContentTypeResolver.inlineViewable("x.txt", "image/png")).isFalse();
        assertThat(ContentTypeResolver.inlineViewable("x.png", "image/png")).isTrue();
        assertThat(ContentTypeResolver.inlineViewable("x.jpg", "image/png")).isFalse();
    }

    @Test
    @DisplayName("TYPE-U6: 판별은 앞부분만 읽는다 (200MB 스트림에서 읽은 바이트가 상한 이하)")
    void readsOnlyHead() throws IOException {
        long size = 200L * 1024 * 1024;
        CountingStream in = new CountingStream(size);
        byte[] head = ContentTypeResolver.readHead(in);
        assertThat(head).hasSize(ContentTypeResolver.DETECT_BYTES);
        assertThat(in.read).isLessThanOrEqualTo(ContentTypeResolver.DETECT_BYTES);
    }

    private static final class CountingStream extends InputStream {
        private final long size;
        private long read;

        CountingStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (read >= size) {
                return -1;
            }
            read++;
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (read >= size) {
                return -1;
            }
            int n = (int) Math.min(len, size - read);
            read += n;
            return n;
        }
    }
}
