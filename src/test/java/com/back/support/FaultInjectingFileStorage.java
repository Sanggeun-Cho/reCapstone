package com.back.support;

import com.back.file.storage.FileStorage;
import com.back.file.storage.S3FileStorage;
import com.back.file.storage.StorageException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 실제 저장소(RustFS)에 넘기면서, 테스트가 장애와 끼어들기를 넣을 수 있게 한 대역.
 * <ul>
 *   <li>{@link #down()}: 모든 호출이 저장소 오류</li>
 *   <li>{@link #failPutAt(int)}: n번째 업로드만 전송 도중 실패</li>
 *   <li>{@link #failDeletes()}: 객체 삭제만 실패</li>
 *   <li>{@link #afterPut(Runnable)}: 업로드가 저장소에 닿은 뒤 기록 단계 전에 실행 (래치로 멈추기)</li>
 * </ul>
 */
public class FaultInjectingFileStorage implements FileStorage {

    private final S3FileStorage delegate;
    private final AtomicInteger puts = new AtomicInteger();
    private volatile boolean down;
    private volatile boolean failDeletes;
    private volatile int failPutAt;
    private volatile Runnable afterPut = () -> {
    };

    public FaultInjectingFileStorage(S3FileStorage delegate) {
        this.delegate = delegate;
    }

    public void down() {
        down = true;
    }

    public void failPutAt(int callNumber) {
        failPutAt = callNumber;
    }

    public void failDeletes() {
        failDeletes = true;
    }

    public void afterPut(Runnable hook) {
        afterPut = hook;
    }

    public void reset() {
        down = false;
        failDeletes = false;
        failPutAt = 0;
        puts.set(0);
        afterPut = () -> {
        };
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        checkUp();
        if (puts.incrementAndGet() == failPutAt) {
            // 전송 도중 끊긴 것처럼 일부만 읽고 실패한다
            InputStream truncated = new InputStream() {
                private long read;

                @Override
                public int read() throws java.io.IOException {
                    if (read++ >= Math.min(size - 1, 1024)) {
                        throw new java.io.IOException("connection reset (test)");
                    }
                    return content.read();
                }
            };
            delegate.put(key, truncated, size, contentType);
            return;
        }
        delegate.put(key, content, size, contentType);
        afterPut.run();
    }

    @Override
    public void delete(String key) {
        checkUp();
        if (failDeletes) {
            throw new StorageException(new IllegalStateException("delete failed (test)"));
        }
        delegate.delete(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {
        checkUp();
        if (failDeletes) {
            throw new StorageException(new IllegalStateException("delete failed (test)"));
        }
        delegate.deleteByPrefix(prefix);
    }

    @Override
    public void list(String prefix, Consumer<StoredObject> consumer) {
        checkUp();
        delegate.list(prefix, consumer);
    }

    @Override
    public boolean exists(String key) {
        return delegate.exists(key);
    }

    @Override
    public URL presignGet(String key, String contentDisposition, String contentType, Duration ttl) {
        checkUp();
        return delegate.presignGet(key, contentDisposition, contentType, ttl);
    }

    /** 테스트가 저장소를 직접 확인할 때 쓴다. 장애 설정과 관계없다. */
    public S3FileStorage real() {
        return delegate;
    }

    private void checkUp() {
        if (down) {
            throw new StorageException(new IllegalStateException("storage down (test)"));
        }
    }
}
