package com.back.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

/**
 * 인증된 사용자의 신원. 세션에 저장되며, 권한은 담지 않는다 (권한은 요청마다 상태로 계산한다. 정책 4.1).
 * <p>
 * {@link #getName()}이 사용자 id를 돌려주므로 세션 저장소가 사용자별 세션을 찾는 색인으로 쓴다.
 */
public record AuthUser(long userId) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
