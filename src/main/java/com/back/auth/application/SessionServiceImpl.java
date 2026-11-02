package com.back.auth.application;

import com.back.auth.api.SessionDto;
import com.back.auth.support.Hashing;
import com.back.auth.support.SessionAttributes;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Spring Session의 사용자별 색인(principal name = 사용자 id)으로 세션을 찾는다.
 */
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final int PUBLIC_ID_LENGTH = 32;

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Override
    public List<SessionDto.DetailRes> list(long userId, String currentSessionId) {
        return sessionsOf(userId).values().stream()
                .map(session -> new SessionDto.DetailRes(
                        publicId(session.getId()),
                        session.getAttribute(SessionAttributes.DEVICE),
                        session.getCreationTime(),
                        session.getLastAccessedTime(),
                        session.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionDto.DetailRes::lastUsedAt).reversed())
                .toList();
    }

    @Override
    public boolean revoke(long userId, String sessionPublicId, String currentSessionId) {
        String sessionId = sessionsOf(userId).keySet().stream()
                .filter(id -> Hashing.constantTimeEquals(publicId(id), sessionPublicId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        sessionRepository.deleteById(sessionId);
        return sessionId.equals(currentSessionId);
    }

    @Override
    public void revokeOthers(long userId, String currentSessionId) {
        sessionsOf(userId).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .forEach(sessionRepository::deleteById);
    }

    @Override
    public void revokeAll(long userId) {
        sessionsOf(userId).keySet().forEach(sessionRepository::deleteById);
    }

    private Map<String, ? extends Session> sessionsOf(long userId) {
        return sessionRepository.findByPrincipalName(String.valueOf(userId));
    }

    static String publicId(String sessionId) {
        return Hashing.sha256Hex(sessionId).substring(0, PUBLIC_ID_LENGTH);
    }
}
