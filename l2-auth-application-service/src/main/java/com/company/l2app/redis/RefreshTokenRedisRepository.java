package com.company.l2app.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
public class RefreshTokenRedisRepository {

    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public RefreshTokenRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveRefreshTokenHash(String refreshTokenHash, String clientId, String jti,
                                      long ttlSeconds) {
        var key = REFRESH_PREFIX + refreshTokenHash;
        redis.opsForHash().put(key, "clientId", clientId);
        redis.opsForHash().put(key, "jti", jti);
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    public Map<Object, Object> getRefreshTokenData(String refreshTokenHash) {
        return redis.opsForHash().entries(REFRESH_PREFIX + refreshTokenHash);
    }

    public boolean exists(String refreshTokenHash) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_PREFIX + refreshTokenHash));
    }

    public void delete(String refreshTokenHash) {
        redis.delete(REFRESH_PREFIX + refreshTokenHash);
    }

    public Long getTTL(String refreshTokenHash) {
        return redis.getExpire(REFRESH_PREFIX + refreshTokenHash, TimeUnit.SECONDS);
    }
}
