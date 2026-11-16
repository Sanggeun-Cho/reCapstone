package com.back.file.event;

/**
 * 파일이 영구 삭제되었다. 구독자(S6)는 커밋 후에 그 파일의 벡터를 지운다.
 */
public record FileRemoved(long spaceId, long fileId) {
}
