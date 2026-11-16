package com.back.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.back.file.api.FileDto;
import com.back.file.application.FileService;
import com.back.file.application.StorageDeletionService;
import com.back.file.application.TrashService;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 파일·폴더 테스트 베이스. 그룹 픽스처(bob이 S의 담당자) 위에 업로드·폴더·저장소 확인 도우미를 둔다.
 * 저장소는 테스트마다 새로 만든 스페이스의 키 접두어로 격리된다.
 */
@RecordApplicationEvents
public abstract class FileTestSupport extends GroupTestSupport {

    /** 실제 PNG 시그니처와 IHDR 앞부분 */
    protected static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0);
    protected static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    protected static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1);
    protected static final byte[] HTML = "<!DOCTYPE html><html><head><title>x</title></head><body>x</body></html>"
            .getBytes(StandardCharsets.UTF_8);
    protected static final byte[] TEXT = "plain text content\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    protected DriveItemRepository itemRepository;

    @Autowired
    protected FileService fileService;

    @Autowired
    protected TrashService trashService;

    @Autowired
    protected StorageDeletionService storageDeletionService;

    @Autowired
    protected ApplicationEvents events;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** DB는 테스트마다 id를 처음부터 다시 매기므로, 같은 키 접두어에 앞 테스트의 객체가 남지 않게 저장소도 비운다. */
    @BeforeEach
    void clearStorage() {
        fileStorage.real().deleteByPrefix("spaces/");
    }

    // --- 업로드 ---

    protected static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, null, content);
    }

    protected static MockMultipartFile file(String name, String content) {
        return file(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code params}는 "이름=값" 쌍. 예: {@code "onConflict=REPLACE", "folderId=3"} */
    protected ResultActions upload(String email, long spaceId, List<MockMultipartFile> files, String... params)
            throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/spaces/" + spaceId + "/files");
        files.forEach(builder::file);
        for (String param : params) {
            String[] pair = param.split("=", 2);
            builder.param(pair[0], pair[1]);
        }
        builder.with(csrf());
        if (email != null) {
            builder.cookie(session(email));
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions upload(String email, long spaceId, MockMultipartFile... files) throws Exception {
        return upload(email, spaceId, Arrays.asList(files));
    }

    /** bob이 S에 올리고 만들어진 항목 id를 돌려준다. */
    protected long uploadOne(long spaceId, Long folderId, String name, byte[] content) throws Exception {
        List<String> params = new ArrayList<>();
        if (folderId != null) {
            params.add("folderId=" + folderId);
        }
        String body = upload(BOB, spaceId, List.of(file(name, content)), params.toArray(String[]::new))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.created[0].id")).longValue();
    }

    protected long uploadOne(Long folderId, String name, String content) throws Exception {
        return uploadOne(s, folderId, name, content.getBytes(StandardCharsets.UTF_8));
    }

    // --- 서비스로 바로 만드는 픽스처 (bob, S) ---

    protected long folder(Long parentId, String name) {
        return as(bob, () -> fileService.createFolder(bob, s, new FileDto.FolderReq(parentId, name)).id());
    }

    /** 깊이 {@code depth}인 폴더를 만들고 가장 깊은 폴더 id를 돌려준다. */
    protected long folderChain(Long parentId, int depth, String prefix) {
        Long current = parentId;
        for (int i = 1; i <= depth; i++) {
            current = folder(current, prefix + i);
        }
        return current;
    }

    protected void trash(long itemId) {
        as(bob, () -> fileService.trash(bob, s, itemId));
    }

    protected DriveItem item(long id) {
        return itemRepository.findById(id).orElseThrow();
    }

    protected String storageKey(long itemId) {
        return item(itemId).getStorageKey();
    }

    protected boolean objectExists(String key) {
        return fileStorage.real().exists(key);
    }

    protected long objectCount(long spaceId) {
        List<String> keys = new ArrayList<>();
        fileStorage.real().list("spaces/" + spaceId + "/", object -> keys.add(object.key()));
        return keys.size();
    }

    // --- 내려받기 ---

    protected HttpResponse<byte[]> fetch(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected String downloadUrl(String email, long spaceId, long itemId, String disposition) throws Exception {
        String body = post("/api/v1/spaces/" + spaceId + "/files/" + itemId + "/download-url", email,
                body("\"disposition\":\"" + disposition + "\""))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    protected static String items(long spaceId) {
        return "/api/v1/spaces/" + spaceId + "/items";
    }

    protected static String trashUrl(long spaceId) {
        return "/api/v1/spaces/" + spaceId + "/trash";
    }

    protected static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    /** {@code size} 바이트를 메모리에 올리지 않고 흘려보내는 업로드 파일. 크기 경계 테스트에 쓴다. */
    protected static MockMultipartFile sized(String name, long size) {
        return new MockMultipartFile("files", name, null, new byte[] {1}) {
            @Override
            public long getSize() {
                return size;
            }

            @Override
            public boolean isEmpty() {
                return size == 0;
            }

            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private long remaining = size;

                    @Override
                    public int read() {
                        if (remaining <= 0) {
                            return -1;
                        }
                        remaining--;
                        return 'a';
                    }

                    @Override
                    public int read(byte[] b, int off, int len) {
                        if (remaining <= 0) {
                            return -1;
                        }
                        int n = (int) Math.min(len, remaining);
                        Arrays.fill(b, off, off + n, (byte) 'a');
                        remaining -= n;
                        return n;
                    }
                };
            }
        };
    }
}
