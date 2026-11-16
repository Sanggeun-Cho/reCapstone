package com.back.file.storage;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;

/** 저장소를 쓸 수 없을 때. 응답은 503 {@code STORAGE_UNAVAILABLE}이다. */
public class StorageException extends BusinessException {

    public StorageException(Throwable cause) {
        super(ErrorCode.STORAGE_UNAVAILABLE);
        initCause(cause);
    }
}
