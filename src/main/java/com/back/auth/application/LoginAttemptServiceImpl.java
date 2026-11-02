package com.back.auth.application;

import com.back.auth.AuthProperties;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 실패 횟수와 잠금을 Redis에 둔다. 잠금 해제 시각은 값으로 저장해 {@link Clock}으로 판단하고, TTL은 정리용으로만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    static final String UNLOCK_AT = "unlockAt";

    private static final String FAILURES = "auth:login-failures:";
    private static final String LOCK = "auth:login-lock:";
    private static final Duration FAILURE_RETENTION = Duration.ofDays(1);
    private static final Duration CLEANUP_MARGIN = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AuthProperties properties;

    @Override
    public void assertNotLocked(long userId) {
        String value = redis.opsForValue().get(LOCK + userId);
        if (value == null) {
            return;
        }
        Instant unlockAt = Instant.ofEpochMilli(Long.parseLong(value));
        if (clock.instant().isBefore(unlockAt)) {
            throw locked(unlockAt);
        }
        redis.delete(LOCK + userId);
    }

    @Override
    public BusinessException recordFailure(long userId, ErrorCode failure) {
        Long failures = redis.opsForValue().increment(FAILURES + userId);
        redis.expire(FAILURES + userId, FAILURE_RETENTION);
        if (failures == null || failures < properties.maxLoginFailures()) {
            return new BusinessException(failure);
        }

        Instant unlockAt = clock.instant().plus(properties.lockDuration());
        redis.opsForValue().set(LOCK + userId, String.valueOf(unlockAt.toEpochMilli()),
                properties.lockDuration().plus(CLEANUP_MARGIN));
        redis.delete(FAILURES + userId);
        return locked(unlockAt);
    }

    @Override
    public void reset(long userId) {
        redis.delete(FAILURES + userId);
        redis.delete(LOCK + userId);
    }

    private static BusinessException locked(Instant unlockAt) {
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                ErrorCode.ACCOUNT_LOCKED.getMessage() + " 잠금은 " + unlockAt + "에 풀립니다.",
                Map.of(UNLOCK_AT, unlockAt.toString()));
    }
}
