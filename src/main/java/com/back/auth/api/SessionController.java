package com.back.auth.api;

import com.back.auth.application.SessionService;
import com.back.auth.web.SessionLogin;
import com.back.common.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionLogin sessionLogin;

    @GetMapping
    public List<SessionDto.DetailRes> list(@AuthenticationPrincipal AuthUser user, HttpServletRequest request) {
        return sessionService.list(user.userId(), sessionLogin.currentSessionId(request));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthUser user, @PathVariable String sessionId,
            HttpServletRequest request) {
        if (sessionService.revoke(user.userId(), sessionId, sessionLogin.currentSessionId(request))) {
            sessionLogin.logout(request);
        }
    }

    /** 현재 기기를 뺀 나머지 세션을 모두 종료한다. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@AuthenticationPrincipal AuthUser user, HttpServletRequest request) {
        sessionService.revokeOthers(user.userId(), sessionLogin.currentSessionId(request));
    }
}
