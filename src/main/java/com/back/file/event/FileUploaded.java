package com.back.file.event;

/**
 * 파일이 저장되었다(새 파일, 덮어쓰기). 커밋 후에 처리할 구독자(S6 임베딩)는
 * {@code @TransactionalEventListener(AFTER_COMMIT)}로 받는다.
 */
public record FileUploaded(long spaceId, long fileId) {
}
