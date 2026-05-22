package com.company.shipmentsvc.controller;

import com.company.shipmentsvc.dto.ShipmentRequest;
import com.company.shipmentsvc.dto.ShipmentResponse;
import com.company.shipmentsvc.exception.TokenValidationException;
import com.company.shipmentsvc.security.JwtTokenValidator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/shipment")
public class ShipmentController {

    private static final String REQUIRED_SCOPE = "shipment:create";

    private final JwtTokenValidator jwtTokenValidator;
    private final WebClient l2Client;

    public ShipmentController(JwtTokenValidator jwtTokenValidator,
                               WebClient.Builder webClientBuilder,
                               @Value("${l2.base-url}") String l2BaseUrl) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.l2Client = webClientBuilder.baseUrl(l2BaseUrl).build();
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(
            @Valid @RequestBody ShipmentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        var token = authHeader.replace("Bearer ", "");

        // 1. Validate JWT locally (signature + expiry + issuer)
        var jwtResult = jwtTokenValidator.validate(token);
        if (!jwtResult.valid()) {
            throw new TokenValidationException(401, "UNAUTHORIZED",
                    jwtResult.errorCode(), jwtResult.errorMessage());
        }

        // 2. Check scope from JWT claims
        var tokenScopes = Set.of(jwtResult.scope().split("\\s+"));
        if (!tokenScopes.contains(REQUIRED_SCOPE)) {
            throw new TokenValidationException(403, "FORBIDDEN",
                    "AUTH_INSUFFICIENT_SCOPE",
                    "Token does not have the required scope: " + REQUIRED_SCOPE);
        }

        // 3. Check revocation via L2
        var revoked = Boolean.TRUE.equals(
                l2Client.get()
                        .uri("/internal/auth/check-revocation?jti={jti}", jwtResult.jti())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(m -> m.get("revoked"))
                        .defaultIfEmpty(false)
                        .block());
        if (revoked) {
            throw new TokenValidationException(401, "UNAUTHORIZED",
                    "AUTH_TOKEN_REVOKED", "Access token has been revoked");
        }

        // 4. Business logic
        var shipmentId = "SHP-" + System.currentTimeMillis();
        var response = new ShipmentResponse(
                shipmentId, "CREATED",
                request.origin(), request.destination(),
                request.description());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
