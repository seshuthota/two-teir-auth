package com.company.l2app.service;

import com.company.l2app.dto.TokenResponse;
import com.company.l2app.entity.AuthClient;
import com.company.l2app.redis.RefreshTokenRedisRepository;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisRepository tokenRedisRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final SecureRandom secureRandom;

    public TokenService(JwtTokenProvider jwtTokenProvider, TokenRedisRepository tokenRedisRepository,
                         RefreshTokenRedisRepository refreshTokenRedisRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenRedisRepository = tokenRedisRepository;
        this.refreshTokenRedisRepository = refreshTokenRedisRepository;
        this.secureRandom = new SecureRandom();
    }

    public TokenResponse issueTokens(AuthClient client, String scope) {
        var accessToken = jwtTokenProvider.generateAccessToken(client.getClientId(), scope);
        var refreshToken = generateRefreshToken();
        var accessTtl = jwtTokenProvider.getAccessTokenExpirationSeconds();
        var refreshTtl = 7 * 24 * 60 * 60L;

        var jti = extractJti(accessToken);
        tokenRedisRepository.saveAccessTokenMetadata(jti, client.getClientId(), accessTtl);

        var refreshHash = hashRefreshToken(refreshToken);
        refreshTokenRedisRepository.saveRefreshTokenHash(refreshHash, client.getClientId(), jti, refreshTtl);

        return new TokenResponse(accessToken, refreshToken, "Bearer", (int) accessTtl, scope);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var hash = md.digest(refreshToken.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    public TokenResponse issueTokens(String clientId, String scope, String refreshToken) {
        var accessToken = jwtTokenProvider.generateAccessToken(clientId, scope);
        var newRefreshToken = generateRefreshToken();
        var accessTtl = jwtTokenProvider.getAccessTokenExpirationSeconds();

        var jti = extractJti(accessToken);
        tokenRedisRepository.saveAccessTokenMetadata(jti, clientId, accessTtl);

        return new TokenResponse(accessToken, newRefreshToken, "Bearer", (int) accessTtl, scope);
    }

    public String extractJti(String accessToken) {
        var claims = jwtTokenProvider.validateToken(accessToken);
        return claims.getId();
    }

    private String generateRefreshToken() {
        var bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
