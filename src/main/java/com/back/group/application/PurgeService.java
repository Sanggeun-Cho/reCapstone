package com.back.group.application;

/**
 * 보관 기간이 지난 그룹·스페이스의 영구 삭제와 탈퇴자 정보 익명화 (정책 2.6, 7.9).
 */
public interface PurgeService {

    record Result(int groups, int spaces, int anonymizedUsers) {
    }

    Result purgeExpired();
}
