package com.back.file.application;

import com.back.file.api.FileDto;

/** 휴지통 (정책 8.5). 담당자만 쓰고, 인계가 진행되는 동안에는 쓸 수 없다. */
public interface TrashService {

    FileDto.RestoreRes restore(long userId, long spaceId, long itemId);

    void purge(long userId, long spaceId, long itemId);

    void empty(long userId, long spaceId);

    /** 휴지통이 비었는지. 인계 시작 조건이다 (정책 5.1 제4항, S5). */
    boolean isEmpty(long spaceId);

    /** 보관 기간이 지난 휴지통 항목을 영구 삭제한다 (정책 8.5 제5항). 지운 최상위 항목 수를 돌려준다. */
    int purgeExpired();
}
