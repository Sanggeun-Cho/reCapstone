package com.back.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 적합성 테스트: 우리 저장소 코드({@link S3FileStorage})가 AWS가 만든 에뮬레이터(aws-emulator)에서도 같은 의미로 동작하는지 본다
 * (DESIGN_REVIEW.md 6.8). 기본 테스트에서는 빠지고 {@code ./gradlew conformanceTest}로 돈다. CI에서는 필수가 아닌 별도 작업이다.
 * <p>
 * 에뮬레이터는 실행마다 새 키와 인증서를 만든다. {@code EMULATOR_HOME}(키·trust store가 있는 폴더)과
 * {@code EMULATOR_ENDPOINT}가 없으면 건너뛴다. CORS(S3C-03)는 에뮬레이터가 지원하지 않아 넣지 않았다.
 */
@Tag("s3-conformance")
class S3ConformanceTest {

    private static S3Client s3;
    private static S3Presigner presigner;
    private static S3FileStorage storage;
    private static final String BUCKET = "conformance-" + UUID.randomUUID();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void connect() throws IOException {
        String home = System.getenv("EMULATOR_HOME");
        String endpoint = System.getenv("EMULATOR_ENDPOINT");
        assumeTrue(home != null && endpoint != null, "EMULATOR_HOME, EMULATOR_ENDPOINT가 없어 건너뛴다");
        Properties credentials = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(home, "emulator_credentials"))) {
            credentials.load(reader);
        }
        var provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                credentials.getProperty("aws_access_key_id").trim(),
                credentials.getProperty("aws_secret_access_key").trim()));
        s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(provider).forcePathStyle(true).build();
        presigner = S3Presigner.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(provider)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
        storage = new S3FileStorage(s3, presigner, BUCKET);
        storage.createBucketIfMissing(List.of());
    }

    @AfterAll
    static void close() {
        if (s3 != null) {
            storage.deleteByPrefix("");
            s3.deleteBucket(r -> r.bucket(BUCKET));
            s3.close();
            presigner.close();
        }
    }

    private static void put(String key, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.put(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
    }

    private static HttpResponse<String> fetch(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("S3C-01: 내려받기 주소의 Content-Disposition·Content-Type이 응답에 반영되고, 만료되면 403")
    void presign() throws Exception {
        put("spaces/1/files/a", "hello");
        String disposition = "attachment; filename*=UTF-8''%EC%9E%90%EB%A3%8C.pdf";
        HttpResponse<String> response = fetch(
                storage.presignGet("spaces/1/files/a", disposition, "application/pdf", Duration.ofMinutes(5)).toString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition")).hasValue(disposition);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(response.body()).isEqualTo("hello");

        HttpResponse<String> inline = fetch(
                storage.presignGet("spaces/1/files/a", "inline", "image/png", Duration.ofMinutes(5)).toString());
        assertThat(inline.headers().firstValue("Content-Disposition")).hasValue("inline");

        String shortLived = storage.presignGet("spaces/1/files/a", "inline", "text/plain", Duration.ofSeconds(1))
                .toString();
        Instant deadline = Instant.now().plusSeconds(3);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
        }
        assertThat(fetch(shortLived).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("S3C-02: 전송이 끊긴 업로드와 중단한 멀티파트 업로드는 객체도 미완료 업로드도 남기지 않는다")
    void abortedUploads() {
        InputStream broken = new InputStream() {
            private int read;

            @Override
            public int read() throws IOException {
                if (read++ > 1000) {
                    throw new IOException("connection reset (test)");
                }
                return 'a';
            }
        };
        assertThatThrownBy(() -> storage.put("spaces/2/files/broken", broken, 100_000, "text/plain"))
                .isInstanceOf(StorageException.class);
        assertThat(storage.exists("spaces/2/files/broken")).isFalse();

        var upload = s3.createMultipartUpload(r -> r.bucket(BUCKET).key("spaces/2/files/mp"));
        s3.uploadPart(r -> r.bucket(BUCKET).key("spaces/2/files/mp").uploadId(upload.uploadId()).partNumber(1),
                RequestBody.fromBytes(new byte[5 * 1024 * 1024]));
        s3.abortMultipartUpload(r -> r.bucket(BUCKET).key("spaces/2/files/mp").uploadId(upload.uploadId()));
        assertThat(s3.listMultipartUploads(r -> r.bucket(BUCKET)).uploads()).isEmpty();
        assertThat(storage.exists("spaces/2/files/mp")).isFalse();
    }

    @Test
    @DisplayName("S3C-04: 없는 키 삭제는 오류가 아니고, 저장소에 닿지 않으면 StorageException(STORAGE_UNAVAILABLE)")
    void errors() {
        storage.delete("spaces/3/files/nope");
        assertThat(storage.exists("spaces/3/files/nope")).isFalse();

        put("spaces/3/files/a", "a");
        put("spaces/3/files/b", "b");
        storage.deleteByPrefix("spaces/3/");
        assertThat(storage.exists("spaces/3/files/a")).isFalse();

        try (S3Client down = S3Client.builder().endpointOverride(URI.create("https://127.0.0.1:1"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("a", "b")))
                .forcePathStyle(true).build()) {
            S3FileStorage unreachable = new S3FileStorage(down, presigner, BUCKET);
            assertThatThrownBy(() -> unreachable.exists("x")).isInstanceOf(StorageException.class);
        }
    }
}
