package com.back.common.authz;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;

/**
 * 상태별 권한 판정. 서비스 진입부에서 호출한다 (정책 4.1, 4.2).
 * <p>
 * 구조적 불변식은 DB 제약이 맡고, 이 인터페이스는 역할(OWNER, 담당자, 인수자)을 상태에서 계산해 판단한다.
 * 권한표 구현은 S2에서 한다.
 *
 * @param <R> 판정 대상 리소스
 * @param <A> 행위
 */
public interface AccessPolicy<R, A extends Enum<A>> {

    boolean can(long userId, R resource, A action);

    default void check(long userId, R resource, A action) {
        if (!can(userId, resource, action)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
