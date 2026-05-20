package com.company.l2app.redis;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Repository
public class TokenRedisRepository {

    private static final String ACCESS_PREFIX = "auth:access:jti:";
    private static final String REVOKED_PREFIX = "auth:revoked:jti:";
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String CLIENT_SESSIONS_PREFIX = "auth:client:";
    private static final String SESSIONS_SUFFIX = ":sessions";

    private final StringRedisTemplate redis;

    public TokenRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveAccessTokenMetadata(String jti, String clientId, long ttlSeconds) {
        var key = ACCESS_PREFIX + jti;
        redis.opsForHash().put(key, "clientId", clientId);
        redis.opsForHash().put(key, "status", "ACTIVE");
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        redis.opsForSet().add(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX, jti);
        redis.opsForValue().set(SESSION_PREFIX + jti, clientId, Duration.ofSeconds(ttlSeconds));
    }

    public Map<Object, Object> getAccessTokenMetadata(String jti) {
        return redis.opsForHash().entries(ACCESS_PREFIX + jti);
    }

    public boolean isTokenRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(REVOKED_PREFIX + jti));
    }

    public void revokeToken(String jti, long ttlSeconds) {
        var key = REVOKED_PREFIX + jti;
        redis.opsForValue().set(key, "true", Duration.ofSeconds(ttlSeconds));
    }

    public void removeFromClientSessions(String clientId, String jti) {
        redis.opsForSet().remove(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX, jti);
        redis.delete(SESSION_PREFIX + jti);
    }

    public Set<String> getActiveClientSessions(String clientId) {
        var allMembers = redis.opsForSet().members(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX);
        if (allMembers == null || allMembers.isEmpty()) return Set.of();

        var active = new HashSet<String>();
        for (var jti : allMembers) {
            if (Boolean.TRUE.equals(redis.hasKey(SESSION_PREFIX + jti))) {
                active.add(jti);
            } else {
                redis.opsForSet().remove(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX, jti);
            }
        }
        return active;
    }

    public void cleanStaleSessionsForClient(String clientId) {
        var members = redis.opsForSet().members(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX);
        if (members == null) return;
        for (var jti : members) {
            if (!Boolean.TRUE.equals(redis.hasKey(SESSION_PREFIX + jti))) {
                redis.opsForSet().remove(CLIENT_SESSIONS_PREFIX + clientId + SESSIONS_SUFFIX, jti);
            }
        }
    }

    public Set<String> scanClientSessionKeys() {
        var keys = new HashSet<String>();
        try (Cursor<byte[]> cursor = redis.getConnectionFactory().getConnection()
                .scan(ScanOptions.scanOptions().match("auth:client:*:sessions").count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            // fallback: keys pattern (dev only)
            var found = redis.keys("auth:client:*:sessions");
            if (found != null) keys.addAll(found);
        }
        return keys;
    }

    public void deleteAccessTokenMetadata(String jti) {
        redis.delete(ACCESS_PREFIX + jti);
    }

    public void incrementFailedAuth(String clientId, Duration ttl) {
        var key = "auth:failure:" + clientId;
        redis.opsForValue().increment(key);
        redis.expire(key, ttl);
    }

    public int getFailedAuthCount(String clientId) {
        var val = redis.opsForValue().get("auth:failure:" + clientId);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public void resetFailedAuth(String clientId) {
        redis.delete("auth:failure:" + clientId);
    }

    public void lockClient(String clientId, Duration ttl) {
        redis.opsForValue().set("auth:lock:" + clientId, "LOCKED", ttl);
    }

    public boolean isClientLocked(String clientId) {
        return Boolean.TRUE.equals(redis.hasKey("auth:lock:" + clientId));
    }
}
