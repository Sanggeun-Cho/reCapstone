package com.back.file.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * 파일 저장소 (S3 호환). 버킷은 비공개이고, 내려받기는 서명한 주소로만 한다 (정책 8.1).
 * 저장소 오류는 모두 {@link StorageException}으로 바꿔 던진다. 없는 객체를 지우는 것은 오류가 아니다.
 */
public interface FileStorage {

    /** {@code size} 바이트를 그대로 올린다. 실패하면 객체가 남지 않는다. */
    void put(String key, InputStream content, long size, String contentType);

    void delete(String key);

    /** 키가 {@code prefix}로 시작하는 객체를 모두 지운다. */
    void deleteByPrefix(String prefix);

    /** 키가 {@code prefix}로 시작하는 객체를 차례로 넘긴다. */
    void list(String prefix, Consumer<StoredObject> consumer);

    boolean exists(String key);

    /**
     * 내려받기 주소를 발급한다. 응답 헤더 {@code Content-Disposition}과 {@code Content-Type}을 서명에 넣는다.
     */
    URL presignGet(String key, String contentDisposition, String contentType, Duration ttl);

    record StoredObject(String key, Instant lastModified) {
    }
}
