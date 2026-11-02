package com.back.user.application;

/**
 * 탈퇴를 막는 조건 (정책 7.8 제1항). 조건마다 구현을 하나씩 등록한다.
 * <p>
 * 그룹 OWNER·스페이스 담당자 조건은 S2에서, 진행 중인 인계 당사자 조건은 S5에서 구현한다.
 */
public interface WithdrawalGuard {

    /**
     * 탈퇴할 수 없으면 {@code ErrorCode.WITHDRAWAL_BLOCKED}로 이유를 담아 던진다.
     */
    void check(long userId);
}
