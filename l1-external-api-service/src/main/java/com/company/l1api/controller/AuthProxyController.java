package com.company.l1api.controller;

import com.company.l1api.dto.RefreshTokenRequest;
import com.company.l1api.dto.TokenRequest;
import com.company.l1api.dto.TokenResponse;
import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthProxyController {

    private final L2ClientService l2Client;
    private final CorrelationIdService correlationIdService;

    public AuthProxyController(L2ClientService l2Client, CorrelationIdService correlationIdService) {
        this.l2Client = l2Client;
        this.correlationIdService = correlationIdService;
    }

    @PostMapping("/token")
    public Mono<ResponseEntity<TokenResponse>> getToken(@Valid @RequestBody TokenRequest request) {
        var correlationId = correlationIdService.generate();
        Map<String, Object> body = Map.of(
                "grantType", request.grantType(),
                "clientId", request.clientId(),
                "clientSecret", request.clientSecret()
        );
        return l2Client.requestToken(body, correlationId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        var correlationId = correlationIdService.generate();
        Map<String, Object> body = Map.of("refreshToken", request.refreshToken());
        return l2Client.refreshToken(body, correlationId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        var correlationId = correlationIdService.generate();
        var token = authHeader.replace("Bearer ", "");
        return l2Client.logout(token, correlationId)
                .map(v -> ResponseEntity.ok().<Void>build());
    }
}
