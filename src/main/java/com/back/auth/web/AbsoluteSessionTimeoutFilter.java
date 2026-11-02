package com.back.auth.web;

import com.back.auth.support.SessionAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 계속 쓰더라도 로그인한 지 정해진 기간(기본 30일)이 지난 세션을 끝낸다 (정책 7.4 제2항).
 * 미사용 만료(14일)는 세션 저장소의 timeout이 맡는다.
 * <p>
 * 보안 필터 체인에서 보안 컨텍스트를 읽은 직후에 둔다. 빈으로 등록하지 않는다 (서블릿 필터로 중복 등록되지 않게).
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final Duration absoluteTimeout;

    public AbsoluteSessionTimeoutFilter(Clock clock, Duration absoluteTimeout) {
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionAttributes.AUTHENTICATED_AT) instanceof Instant authenticatedAt
                && !clock.instant().isBefore(authenticatedAt.plus(absoluteTimeout))) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
