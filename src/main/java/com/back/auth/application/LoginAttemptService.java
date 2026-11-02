package com.back.auth.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;

/**
 * 계정 기준 로그인 실패 횟수와 잠금 (정책 7.2 제4항). 비밀번호, 일회용 코드, 본인 재확인의 실패를 함께 센다.
 */
public interface LoginAttemptService {

    /**
     * 잠긴 계정이면 {@link ErrorCode#ACCOUNT_LOCKED}를 던진다.
     */
    void assertNotLocked(long userId);

    /**
     * 실패를 기록하고 호출자가 던질 예외를 돌려준다. 이번 실패로 한도에 닿으면 잠금 예외, 아니면 {@code failure} 예외다.
     */
    BusinessException recordFailure(long userId, ErrorCode failure);

    /**
     * 실패 횟수와 잠금을 지운다. 인증에 성공했거나 비밀번호를 재설정했을 때 부른다.
     */
    void reset(long userId);
}
