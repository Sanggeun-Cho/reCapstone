package com.back.auth.web;

import com.back.auth.AuthProperties;
import com.back.auth.support.SessionAttributes;
import com.back.auth.support.UserAgentSummary;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.common.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 결과를 HTTP 세션에 반영한다. 세션 ID는 인증 상태가 바뀔 때마다 새로 만든다 (세션 고정 방지).
 */
@Component
@RequiredArgsConstructor
public class SessionLogin {

    private final SecurityContextRepository securityContextRepository;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * 비밀번호는 맞았고 일회용 코드를 기다리는 상태로 둔다. 이 상태로는 보호된 API를 쓸 수 없다.
     */
    public void startMfa(long userId, HttpServletRequest request) {
        HttpSession session = renewSession(request);
        session.setAttribute(SessionAttributes.MFA_PENDING,
                new MfaPending(userId, clock.instant().plus(properties.mfaPendingTtl())));
    }

    /**
     * 일회용 코드를 기다리는 사용자 id. 없거나 기한이 지났으면 {@code MFA_SESSION_EXPIRED}.
     */
    public long pendingMfaUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionAttributes.MFA_PENDING) instanceof MfaPending pending) {
            if (clock.instant().isBefore(pending.expiresAt())) {
                return pending.userId();
            }
            session.removeAttribute(SessionAttributes.MFA_PENDING);
        }
        throw new BusinessException(ErrorCode.MFA_SESSION_EXPIRED);
    }

    public void establish(long userId, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = renewSession(request);
        session.removeAttribute(SessionAttributes.MFA_PENDING);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthUser(userId), null, List.of()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(userId));
        session.setAttribute(SessionAttributes.DEVICE, UserAgentSummary.of(request.getHeader(HttpHeaders.USER_AGENT)));
        session.setAttribute(SessionAttributes.AUTHENTICATED_AT, clock.instant());
    }

    /** 현재 세션을 끝낸다. 저장소에서 지우고 쿠키도 만료시킨다. */
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    public String currentSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }

    private static HttpSession renewSession(HttpServletRequest request) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        return session;
    }

    record MfaPending(long userId, Instant expiresAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
