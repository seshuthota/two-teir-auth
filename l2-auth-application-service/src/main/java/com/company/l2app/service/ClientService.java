package com.company.l2app.service;

import com.company.l2app.entity.AuthClient;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.repository.AuthClientRepository;
import com.company.l2app.security.ClientSecretHasher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class ClientService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration FAILURE_TTL = Duration.ofMinutes(10);

    private final AuthClientRepository clientRepository;
    private final ClientSecretHasher secretHasher;
    private final TokenRedisRepository tokenRedisRepository;

    public ClientService(AuthClientRepository clientRepository, ClientSecretHasher secretHasher,
                          TokenRedisRepository tokenRedisRepository) {
        this.clientRepository = clientRepository;
        this.secretHasher = secretHasher;
        this.tokenRedisRepository = tokenRedisRepository;
    }

    public Optional<AuthClient> findByClientId(String clientId) {
        return clientRepository.findByClientId(clientId);
    }

    public boolean validateCredentials(AuthClient client, String clientSecret) {
        return secretHasher.matches(clientSecret, client.getClientSecretHash());
    }

    public boolean isActive(AuthClient client) {
        return "ACTIVE".equals(client.getStatus());
    }

    public boolean isClientLocked(String clientId) {
        return tokenRedisRepository.isClientLocked(clientId);
    }

    public void handleFailedAuth(String clientId) {
        tokenRedisRepository.incrementFailedAuth(clientId, FAILURE_TTL);
        var attempts = tokenRedisRepository.getFailedAuthCount(clientId);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            tokenRedisRepository.lockClient(clientId, LOCK_DURATION);
        }
    }

    public void resetFailedAuth(String clientId) {
        tokenRedisRepository.resetFailedAuth(clientId);
    }
}
