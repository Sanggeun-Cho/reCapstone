package com.back.file.storage;

import java.util.UUID;

/** 저장소 객체 키 규칙: {@code spaces/{spaceId}/files/{uuid}} (DESIGN_REVIEW.md 6.8). 키 모양은 이 클래스에서만 만든다. */
public final class StorageKeys {

    /** 모든 파일 객체의 접두어 (고아 객체 정리 범위) */
    public static final String ROOT = "spaces/";

    private StorageKeys() {
    }

    public static String newFileKey(long spaceId) {
        return spacePrefix(spaceId) + "files/" + UUID.randomUUID();
    }

    /** 스페이스의 모든 객체 (스페이스 영구 삭제) */
    public static String spacePrefix(long spaceId) {
        return ROOT + spaceId + "/";
    }
}
