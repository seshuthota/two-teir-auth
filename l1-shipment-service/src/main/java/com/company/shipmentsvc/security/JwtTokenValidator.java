package com.company.shipmentsvc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenValidator {

    private final PublicKey publicKey;
    private final String issuer;
    private final long clockSkewSeconds;

    public JwtTokenValidator(
            @Value("${auth.jwt.public-key}") String publicKeyPem,
            @Value("${auth.jwt.expected-issuer:l2-auth-application-service}") String issuer,
            @Value("${auth.jwt.clock-skew-seconds:30}") long clockSkewSeconds) {
        this.publicKey = loadPublicKey(publicKeyPem);
        this.issuer = issuer;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public ValidationResult validate(String token) {
        Claims claims;
        try {
            var parser = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(issuer)
                    .build();
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            return ValidationResult.invalid("AUTH_TOKEN_INVALID", "Invalid access token");
        }

        if (isExpired(claims)) {
            return ValidationResult.invalid("AUTH_TOKEN_EXPIRED", "Access token expired");
        }

        var clientId = claims.get("clientId", String.class);
        var scope = claims.get("scope", String.class);
        var jti = claims.getId();

        return ValidationResult.valid(clientId, jti, scope);
    }

    private boolean isExpired(Claims claims) {
        var graceInstant = Instant.now().plusSeconds(clockSkewSeconds);
        return claims.getExpiration().before(Date.from(graceInstant));
    }

    private PublicKey loadPublicKey(String pem) {
        try {
            var cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            var keyBytes = Base64.getDecoder().decode(cleaned);
            var spec = new X509EncodedKeySpec(keyBytes);
            var kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key", e);
        }
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
