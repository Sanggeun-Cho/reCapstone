package com.back.common.authz;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;

/**
 * 상태별 권한 판정. 서비스 진입부에서 호출한다 (정책 4.1, 4.2).
 * <p>
 * 구조적 불변식은 DB 제약이 맡고, 이 인터페이스는 역할(OWNER, 담당자, 인수자)을 상태에서 계산해 판단한다.
 * <p>
 * 볼 수 없는 리소스는 404, 볼 수는 있지만 그 행위를 할 수 없으면 403으로 답한다 ({@link #checkVisible}).
 * 볼 수 없는 사람에게 403을 주면 그 리소스가 있다는 사실이 드러나기 때문이다 (정책 4.3 제3항).
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

    /**
     * {@code viewAction}을 할 수 없으면 리소스가 없는 것처럼 404, 볼 수는 있지만 {@code action}을 할 수 없으면 403.
     */
    default void checkVisible(long userId, R resource, A viewAction, A action) {
        if (!can(userId, resource, viewAction)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        check(userId, resource, action);
    }
}
