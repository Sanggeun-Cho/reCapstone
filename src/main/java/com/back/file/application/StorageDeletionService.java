package com.back.file.application;

import java.util.Collection;

/**
 * 저장소 객체 삭제 대기열 (DESIGN_REVIEW.md 6.8 구현 메모).
 * DB 행 삭제와 같은 트랜잭션에서 대기열에 넣고, 커밋 후에 객체를 지운다. 성공하면 대기열 행을 지우고, 실패하면 남겨 예약 작업이 다시 지운다.
 */
public interface StorageDeletionService {

    /** 호출한 트랜잭션 안에서 객체 키를 대기열에 넣는다. */
    void enqueueKeys(Collection<String> storageKeys);

    /** 호출한 트랜잭션 안에서 스페이스의 모든 객체(키 접두어)를 대기열에 넣는다 (스페이스 영구 삭제). */
    void enqueueSpaces(Collection<Long> spaceIds);

    /** 대기열에 남은 삭제를 다시 시도한다. 처리한 건수를 돌려준다. */
    int retryPending();

    static String spacePrefix(long spaceId) {
        return "spaces/" + spaceId + "/";
    }
}
