package com.back.auth.api;

import java.time.Instant;

public final class SessionDto {

    private SessionDto() {
    }

    /**
     * @param id      세션 ID의 해시. 세션 ID 원문은 인증 비밀이라 내보내지 않는다
     * @param device  "브라우저 / 운영체제" 요약
     * @param current 이 요청을 보낸 세션인지
     */
    public record DetailRes(String id, String device, Instant createdAt, Instant lastUsedAt, boolean current) {
    }
}
