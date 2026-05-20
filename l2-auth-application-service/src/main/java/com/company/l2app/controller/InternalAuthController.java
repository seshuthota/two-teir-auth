package com.company.l2app.controller;

import com.company.l2app.dto.RefreshTokenRequest;
import com.company.l2app.dto.TokenRequest;
import com.company.l2app.dto.TokenResponse;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.service.*;
import com.company.l2app.security.TokenValidator;
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

    public InternalAuthController(ClientService clientService, TokenService tokenService,
                                   RefreshTokenService refreshTokenService, ScopeService scopeService,
                                   AuditService auditService, TokenValidator tokenValidator,
                                   TokenRedisRepository tokenRedisRepository) {
        this.clientService = clientService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.tokenValidator = tokenValidator;
        this.tokenRedisRepository = tokenRedisRepository;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> issueToken(@RequestBody TokenRequest request) {
        var clientOpt = clientService.findByClientId(request.clientId());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(null);
        }

        var client = clientOpt.get();
        if (!clientService.isActive(client)) {
            return ResponseEntity.status(403).body(null);
        }

        if (clientService.isClientLocked(client.getClientId())) {
            return ResponseEntity.status(429).body(null);
        }

        if (!clientService.validateCredentials(client, request.clientSecret())) {
            clientService.handleFailedAuth(client.getClientId());
            auditService.log(client.getClientId(), null, "LOGIN_FAILED", "FAILURE",
                    "Invalid client credentials");
            return ResponseEntity.status(401).body(null);
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
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            var response = refreshTokenService.refresh(request.refreshToken());
            return ResponseEntity.ok(response);
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        var token = authHeader.replace("Bearer ", "");
        var result = tokenValidator.validate(token);
        if (!result.valid()) {
            return ResponseEntity.status(401).build();
        }

        // Revoke access token
        tokenRedisRepository.revokeToken(result.jti(), 900);
        tokenRedisRepository.deleteAccessTokenMetadata(result.jti());
        tokenRedisRepository.removeFromClientSessions(result.clientId(), result.jti());
        auditService.log(result.clientId(), result.jti(), "TOKEN_REVOKED", "SUCCESS");

        return ResponseEntity.ok().build();
    }
}
