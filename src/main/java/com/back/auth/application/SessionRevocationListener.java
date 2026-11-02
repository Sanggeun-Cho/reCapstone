package com.back.auth.application;

import com.back.user.application.UserEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계정 변경이 커밋된 뒤에 세션을 종료한다. 트랜잭션이 롤백되면 세션은 그대로 둔다.
 */
@Component
@RequiredArgsConstructor
public class SessionRevocationListener {

    private final SessionService sessionService;

    @TransactionalEventListener(fallbackExecution = true)
    public void on(UserEvents.Withdrawn event) {
        sessionService.revokeAll(event.userId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(UserEvents.PasswordChanged event) {
        if (event.keepSessionId() == null) {
            sessionService.revokeAll(event.userId());
        } else {
            sessionService.revokeOthers(event.userId(), event.keepSessionId());
        }
    }
}
