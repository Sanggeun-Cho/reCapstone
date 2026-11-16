package com.back.file.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

/**
 * 파일 형식 판단 (정책 8.6).
 * <ul>
 *   <li>표시용 형식: 현재 확장자로 정한다.</li>
 *   <li>실제 형식: 업로드할 때 내용의 앞부분으로 판별해 저장한다. 이름은 판별에 쓰지 않는다.
 *       이름을 함께 주면 텍스트 내용의 {@code b.pdf}를 PDF로 볼 수 있기 때문이다.</li>
 *   <li>바로 열기: 현재 확장자가 허용 목록에 있고, 저장된 실제 형식이 그 확장자와 맞을 때만 (SVG 제외).</li>
 * </ul>
 */
public final class ContentTypeResolver {

    /** 실제 형식 판별에 읽는 앞부분 크기. 큰 파일도 이만큼만 메모리에 올린다 */
    public static final int DETECT_BYTES = 64 * 1024;

    public static final String OCTET_STREAM = "application/octet-stream";

    /** 바로 열기 허용 확장자와 그 실제 형식 (베타 범위, 정책 8.6 제1항) */
    private static final Map<String, String> INLINE = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    private static final Tika TIKA = new Tika();

    private ContentTypeResolver() {
    }

    /** 현재 확장자로 정한 표시용 형식. 확장자가 없거나 모르면 {@code application/octet-stream}. */
    public static String byName(String name) {
        String extension = extension(name);
        if (extension.isEmpty()) {
            return OCTET_STREAM;
        }
        String detected = TIKA.detect(name);
        return detected == null ? OCTET_STREAM : detected;
    }

    /** 내용의 앞부분(최대 {@link #DETECT_BYTES})으로 판별한 실제 형식. */
    public static String detect(byte[] head) {
        String detected = TIKA.detect(head);
        return detected == null ? OCTET_STREAM : MediaType.parse(detected).getBaseType().toString();
    }

    /** 스트림에서 앞부분을 읽는다. 스트림이 짧으면 있는 만큼만 돌려준다. */
    public static byte[] readHead(InputStream in) throws IOException {
        return in.readNBytes(DETECT_BYTES);
    }

    /** 현재 이름과 저장된 실제 형식으로 바로 열 수 있는지 판단한다. 이름을 바꾸면 다시 부른다. */
    public static boolean inlineViewable(String name, String detectedType) {
        String expected = INLINE.get(extension(name));
        return expected != null && expected.equals(detectedType);
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
