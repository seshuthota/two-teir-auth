package com.company.l2app.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupTask.class);

    private final TokenRedisRepository tokenRedisRepository;

    public SessionCleanupTask(TokenRedisRepository tokenRedisRepository) {
        this.tokenRedisRepository = tokenRedisRepository;
    }

    @Scheduled(fixedRateString = "${auth.session.cleanup-interval-ms:300000}")
    public void cleanStaleSessions() {
        var keys = tokenRedisRepository.scanClientSessionKeys();
        int cleaned = 0;
        for (var key : keys) {
            var clientId = extractClientId(key);
            if (clientId != null) {
                tokenRedisRepository.cleanStaleSessionsForClient(clientId);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.debug("Cleaned stale sessions for {} clients", cleaned);
        }
    }

    private String extractClientId(String redisKey) {
        var prefix = "auth:client:";
        var suffix = ":sessions";
        if (redisKey.startsWith(prefix) && redisKey.endsWith(suffix)) {
            return redisKey.substring(prefix.length(), redisKey.length() - suffix.length());
        }
        return null;
    }
}
