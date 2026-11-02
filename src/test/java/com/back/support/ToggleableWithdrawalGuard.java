package com.back.support;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.application.WithdrawalGuard;

/**
 * 탈퇴 차단 지점 검증용 (USER-10b). 기본은 통과하고, 켜면 탈퇴를 막는다.
 */
public class ToggleableWithdrawalGuard implements WithdrawalGuard {

    private volatile boolean blocking;

    public void block() {
        blocking = true;
    }

    public void reset() {
        blocking = false;
    }

    @Override
    public void check(long userId) {
        if (blocking) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_BLOCKED, "테스트: 담당 중인 스페이스가 있습니다.");
        }
    }
}
