package com.back.auth.application;

import com.back.auth.api.SessionDto;
import java.util.List;

/**
 * 로그인 세션(기기) 관리 (정책 7.4). 세션 ID 원문 대신 해시값({@code id})으로 세션을 가리킨다.
 */
public interface SessionService {

    List<SessionDto.DetailRes> list(long userId, String currentSessionId);

    /**
     * 내 세션 하나를 종료한다. 다른 사용자의 세션이면 {@code NOT_FOUND}.
     *
     * @return 종료한 세션이 이 요청의 세션이면 true (호출자가 현재 세션을 무효화해야 한다)
     */
    boolean revoke(long userId, String sessionPublicId, String currentSessionId);

    void revokeOthers(long userId, String currentSessionId);

    void revokeAll(long userId);
}
