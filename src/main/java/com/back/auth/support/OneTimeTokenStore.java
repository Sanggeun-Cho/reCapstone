package com.back.auth.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 메일 링크용 일회용 토큰 (이메일 인증, 비밀번호 재설정). Redis에는 토큰의 해시만 저장한다.
 * <p>
 * 사용자당 목적별로 유효한 토큰은 하나다. 다시 발급하면 이전 토큰은 무효가 된다.
 * 만료는 저장해 둔 만료 시각과 {@link Clock}으로 판단하고, Redis TTL은 정리용으로만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class OneTimeTokenStore {

    private static final String PREFIX = "auth:ott:";

    private final StringRedisTemplate redis;
    private final Clock clock;

    public enum Purpose {
        EMAIL_VERIFICATION("email-verification"),
        PASSWORD_RESET("password-reset");

        private final String key;

        Purpose(String key) {
            this.key = key;
        }
    }

    public String issue(Purpose purpose, long userId, Duration ttl) {
        String userKey = userKey(purpose, userId);
        String previousHash = redis.opsForValue().get(userKey);
        if (previousHash != null) {
            redis.delete(tokenKey(purpose, previousHash));
        }

        String token = Hashing.randomUrlToken();
        String hash = Hashing.sha256Hex(token);
        Instant expiresAt = clock.instant().plus(ttl);
        redis.opsForValue().set(tokenKey(purpose, hash), userId + ":" + expiresAt.toEpochMilli(), ttl);
        redis.opsForValue().set(userKey, hash, ttl);
        return token;
    }

    /**
     * 토큰을 한 번 쓰고 지운다. 없거나 만료된 토큰이면 비어 있다.
     */
    public Optional<Long> consume(Purpose purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String hash = Hashing.sha256Hex(token);
        String value = redis.opsForValue().getAndDelete(tokenKey(purpose, hash));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":");
        long userId = Long.parseLong(parts[0]);
        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[1]));

        String userKey = userKey(purpose, userId);
        if (hash.equals(redis.opsForValue().get(userKey))) {
            redis.delete(userKey);
        }
        return clock.instant().isBefore(expiresAt) ? Optional.of(userId) : Optional.empty();
    }

    private static String tokenKey(Purpose purpose, String hash) {
        return PREFIX + purpose.key + ":" + hash;
    }

    private static String userKey(Purpose purpose, long userId) {
        return PREFIX + purpose.key + ":user:" + userId;
    }
}
