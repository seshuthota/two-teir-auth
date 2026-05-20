package com.company.l2app.security;

import com.company.l2app.redis.TokenRedisRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

@Component
public class TokenValidator {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisRepository tokenRedisRepository;

    public TokenValidator(JwtTokenProvider jwtTokenProvider, TokenRedisRepository tokenRedisRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenRedisRepository = tokenRedisRepository;
    }

    public ValidationResult validate(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            return ValidationResult.invalid("AUTH_TOKEN_INVALID", "Invalid access token");
        }

        if (jwtTokenProvider.isTokenExpired(claims)) {
            return ValidationResult.invalid("AUTH_TOKEN_EXPIRED", "Access token expired");
        }

        var jti = claims.getId();
        if (tokenRedisRepository.isTokenRevoked(jti)) {
            return ValidationResult.invalid("AUTH_TOKEN_REVOKED", "Access token has been revoked");
        }

        var clientId = claims.get("clientId", String.class);
        var scope = claims.get("scope", String.class);
        return ValidationResult.valid(clientId, jti, scope);
    }

    public record ValidationResult(
            boolean valid,
            String clientId,
            String jti,
            String scope,
            String errorCode,
            String errorMessage
    ) {
        public static ValidationResult valid(String clientId, String jti, String scope) {
            return new ValidationResult(true, clientId, jti, scope, null, null);
        }

        public static ValidationResult invalid(String errorCode, String errorMessage) {
            return new ValidationResult(false, null, null, null, errorCode, errorMessage);
        }
    }
}
