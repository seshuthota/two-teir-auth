package com.company.l2app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final long accessTokenExpirationSeconds;
    private final long clockSkewSeconds;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${auth.jwt.private-key}") String privateKeyPem,
            @Value("${auth.jwt.public-key}") String publicKeyPem,
            @Value("${auth.jwt.key-id}") String keyId,
            @Value("${auth.jwt.access-token-expiration}") long accessTokenExpirationSeconds,
            @Value("${auth.jwt.clock-skew-seconds:30}") long clockSkewSeconds) {
        this.privateKey = loadPrivateKey(privateKeyPem);
        this.publicKey = loadPublicKey(publicKeyPem);
        this.keyId = keyId;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.clockSkewSeconds = clockSkewSeconds;
        this.issuer = "l2-auth-application-service";
    }

    public String generateAccessToken(String clientId, String scope) {
        var now = Instant.now();
        var jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .issuer(issuer)
                .subject(clientId)
                .claim("clientId", clientId)
                .claim("scope", scope)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .header().keyId(keyId).and()
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateToken(String token) {
        var parser = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build();
        return parser.parseSignedClaims(token).getPayload();
    }

    public boolean isTokenExpired(Claims claims) {
        var graceInstant = Instant.now().plusSeconds(clockSkewSeconds);
        return claims.getExpiration().before(Date.from(graceInstant));
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    private PrivateKey loadPrivateKey(String pem) {
        try {
            var keyBytes = parsePemKey(pem, "PRIVATE KEY");
            var spec = new PKCS8EncodedKeySpec(keyBytes);
            var kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key", e);
        }
    }

    private PublicKey loadPublicKey(String pem) {
        try {
            var keyBytes = parsePemKey(pem, "PUBLIC KEY");
            var spec = new X509EncodedKeySpec(keyBytes);
            var kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key", e);
        }
    }

    private byte[] parsePemKey(String pem, String label) {
        var cleaned = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }
}
