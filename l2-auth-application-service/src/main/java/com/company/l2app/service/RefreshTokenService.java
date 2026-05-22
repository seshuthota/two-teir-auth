package com.company.l2app.service;

import com.company.l2app.dto.TokenResponse;
import com.company.l2app.redis.RefreshTokenRedisRepository;
import com.company.l2app.redis.TokenRedisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final TokenRedisRepository tokenRedisRepository;
    private final TokenService tokenService;
    private final ScopeService scopeService;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenService(RefreshTokenRedisRepository refreshTokenRedisRepository,
                                TokenRedisRepository tokenRedisRepository, TokenService tokenService,
                                ScopeService scopeService,
                                @Value("${auth.jwt.refresh-token-expiration}") long refreshTokenTtlSeconds) {
        this.refreshTokenRedisRepository = refreshTokenRedisRepository;
        this.tokenRedisRepository = tokenRedisRepository;
        this.tokenService = tokenService;
        this.scopeService = scopeService;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public TokenResponse refresh(String refreshToken) {
        var hash = hashRefreshToken(refreshToken);
        if (!refreshTokenRedisRepository.exists(hash)) {
            throw new InvalidRefreshTokenException("Refresh token not found or expired");
        }

        var data = refreshTokenRedisRepository.getRefreshTokenData(hash);
        var clientId = (String) data.get("clientId");
        var oldJti = (String) data.get("jti");

        refreshTokenRedisRepository.delete(hash);
        tokenRedisRepository.removeFromClientSessions(clientId, oldJti);
        tokenRedisRepository.deleteAccessTokenMetadata(oldJti);

        var scope = scopeService.getClientScopeString(clientId);
        var newTokenResponse = tokenService.issueTokens(clientId, scope);

        var newJti = tokenService.extractJti(newTokenResponse.accessToken());
        var refreshHash = hashRefreshToken(newTokenResponse.refreshToken());
        refreshTokenRedisRepository.saveRefreshTokenHash(refreshHash, clientId, newJti, refreshTokenTtlSeconds);

        return newTokenResponse;
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var hash = md.digest(refreshToken.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
