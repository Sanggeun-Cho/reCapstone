package com.back.file.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * AWS SDK v2로 구현한 저장소. 주소만 바꾸면 AWS S3와 로컬 S3 호환 저장소(RustFS)에 같은 코드로 붙는다.
 */
public class S3FileStorage implements FileStorage {

    /** DeleteObjects 한 번에 지울 수 있는 최대 개수 */
    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            s3.putObject(r -> r.bucket(bucket).key(key).contentType(contentType).contentLength(size),
                    RequestBody.fromInputStream(content, size));
        } catch (SdkException | IllegalStateException e) {
            // 스트림이 끊기면 SDK가 IllegalStateException을 던진다. 저장소에는 객체가 만들어지지 않는다
            throw new StorageException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteByPrefix(String prefix) {
        List<ObjectIdentifier> batch = new ArrayList<>();
        list(prefix, object -> {
            batch.add(ObjectIdentifier.builder().key(object.key()).build());
            if (batch.size() == DELETE_BATCH) {
                deleteBatch(batch);
            }
        });
        deleteBatch(batch);
    }

    private void deleteBatch(List<ObjectIdentifier> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> keys = List.copyOf(batch);
        batch.clear();
        try {
            var result = s3.deleteObjects(r -> r.bucket(bucket).delete(d -> d.objects(keys).quiet(true)));
            if (result.hasErrors() && !result.errors().isEmpty()) {
                throw new StorageException(new IllegalStateException("delete failed: " + result.errors().getFirst()));
            }
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void list(String prefix, Consumer<StoredObject> consumer) {
        try {
            for (S3Object object : s3.listObjectsV2Paginator(r -> r.bucket(bucket).prefix(prefix)).contents()) {
                consumer.accept(new StoredObject(object.key(), object.lastModified()));
            }
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(r -> r.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException(e);
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public URL presignGet(String key, String contentDisposition, String contentType, Duration ttl) {
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(r -> r.bucket(bucket).key(key)
                            .responseContentDisposition(contentDisposition)
                            .responseContentType(contentType))
                    .build()).url();
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    /** 버킷이 없으면 만들고, 프론트엔드 주소에서 GET을 허용하는 CORS를 설정한다 (로컬·테스트 전용). */
    public void createBucketIfMissing(List<String> corsOrigins) {
        try {
            try {
                s3.headBucket(r -> r.bucket(bucket));
            } catch (NoSuchBucketException e) {
                s3.createBucket(r -> r.bucket(bucket));
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                s3.createBucket(r -> r.bucket(bucket));
            }
            if (!corsOrigins.isEmpty()) {
                // 뷰어(pdf.js 등)가 파일을 JS로 받으려면 프론트 주소의 GET을 허용해야 한다 (DESIGN_REVIEW.md 6.8)
                s3.putBucketCors(r -> r.bucket(bucket).corsConfiguration(c -> c.corsRules(CORSRule.builder()
                        .allowedOrigins(corsOrigins)
                        .allowedMethods("GET")
                        .allowedHeaders("*")
                        .exposeHeaders("Content-Disposition", "Content-Type", "Content-Length")
                        .maxAgeSeconds(3000)
                        .build())));
            }
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }
}
