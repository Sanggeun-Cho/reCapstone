package com.back.user.application;

/**
 * 사용자 계정 이벤트. 세션 종료처럼 다른 기능이 커밋 이후에 처리할 일을 알린다.
 */
public final class UserEvents {

    private UserEvents() {
    }

    /**
     * 탈퇴. 모든 세션을 종료하고, 그룹 멤버십 정리(S2)도 이 이벤트로 붙인다.
     */
    public record Withdrawn(long userId) {
    }

    /**
     * 비밀번호 변경 또는 재설정.
     *
     * @param keepSessionId 남겨 둘 세션(변경을 요청한 현재 기기). 재설정이면 null이며 모든 세션을 종료한다
     */
    public record PasswordChanged(long userId, String keepSessionId) {
    }
}
