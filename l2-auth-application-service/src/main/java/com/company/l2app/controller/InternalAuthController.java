package com.company.l2app.controller;

import com.company.l2app.dto.RefreshTokenRequest;
import com.company.l2app.dto.TokenRequest;
import com.company.l2app.dto.TokenResponse;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.service.*;
import com.company.l2app.security.TokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final ClientService clientService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final TokenValidator tokenValidator;
    private final TokenRedisRepository tokenRedisRepository;
    private final long accessTokenExpirationSeconds;

    public InternalAuthController(ClientService clientService, TokenService tokenService,
                                    RefreshTokenService refreshTokenService, ScopeService scopeService,
                                    AuditService auditService, TokenValidator tokenValidator,
                                    TokenRedisRepository tokenRedisRepository,
                                    @Value("${auth.jwt.access-token-expiration}") long accessTokenExpirationSeconds) {
        this.clientService = clientService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.tokenValidator = tokenValidator;
        this.tokenRedisRepository = tokenRedisRepository;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    @PostMapping("/token")
    public ResponseEntity<?> issueToken(@RequestBody TokenRequest request) {
        if (!"client_credentials".equals(request.grantType())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "Bad Request",
                    "code", "AUTH_INVALID_GRANT_TYPE", "message", "Unsupported grant type"
            ));
        }

        var clientOpt = clientService.findByClientId(request.clientId());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "Unauthorized",
                    "code", "AUTH_CLIENT_NOT_FOUND", "message", "Client not found"
            ));
        }

        var client = clientOpt.get();
        if (!clientService.isActive(client)) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", 403, "error", "Forbidden",
                    "code", "AUTH_CLIENT_INACTIVE", "message", "Client is not active"
            ));
        }

        if (clientService.isClientLocked(client.getClientId())) {
            return ResponseEntity.status(429).body(Map.of(
                    "status", 429, "error", "Too Many Requests",
                    "code", "AUTH_CLIENT_LOCKED", "message", "Client is locked due to too many failed attempts"
            ));
        }

        if (!clientService.validateCredentials(client, request.clientSecret())) {
            clientService.handleFailedAuth(client.getClientId());
            auditService.log(client.getClientId(), null, "LOGIN_FAILED", "FAILURE",
                    "Invalid client credentials");
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "Unauthorized",
                    "code", "AUTH_INVALID_CREDENTIALS", "message", "Invalid client credentials"
            ));
        }

        clientService.resetFailedAuth(client.getClientId());
        var scope = scopeService.getClientScopeString(client.getClientId());
        var tokenResponse = tokenService.issueTokens(client, scope);
        auditService.log(client.getClientId(),
                tokenService.extractJti(tokenResponse.accessToken()),
                "TOKEN_ISSUED", "SUCCESS");

        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            var response = refreshTokenService.refresh(request.refreshToken());
            return ResponseEntity.ok(response);
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "Unauthorized",
                    "code", "AUTH_INVALID_REFRESH_TOKEN", "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        var token = authHeader.replace("Bearer ", "");
        var result = tokenValidator.validate(token);
        if (!result.valid()) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "Unauthorized",
                    "code", "AUTH_TOKEN_INVALID", "message", "Token is invalid or expired"
            ));
        }

        // Revoke access token
        tokenRedisRepository.revokeToken(result.jti(), accessTokenExpirationSeconds);
        tokenRedisRepository.deleteAccessTokenMetadata(result.jti());
        tokenRedisRepository.removeFromClientSessions(result.clientId(), result.jti());
        auditService.log(result.clientId(), result.jti(), "TOKEN_REVOKED", "SUCCESS");

        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-revocation")
    public ResponseEntity<Map<String, Object>> checkRevocation(@RequestParam String jti) {
        var revoked = tokenRedisRepository.isTokenRevoked(jti);
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }
}
