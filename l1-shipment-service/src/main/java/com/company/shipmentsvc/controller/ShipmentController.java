package com.company.shipmentsvc.controller;

import com.company.shipmentsvc.dto.*;
import com.company.shipmentsvc.exception.TokenValidationException;
import com.company.shipmentsvc.security.JwtTokenValidator;
import com.company.shipmentsvc.service.ShipmentService;
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

    private static final String SCOPE_CREATE = "shipment:create";
    private static final String SCOPE_READ = "shipment:read";
    private static final String SCOPE_UPDATE = "shipment:update";
    private static final String SCOPE_DELETE = "shipment:delete";

    private final JwtTokenValidator jwtTokenValidator;
    private final WebClient l2Client;
    private final ShipmentService shipmentService;

    public ShipmentController(JwtTokenValidator jwtTokenValidator,
                               WebClient.Builder webClientBuilder,
                               @Value("${l2.base-url}") String l2BaseUrl,
                               ShipmentService shipmentService) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.l2Client = webClientBuilder.baseUrl(l2BaseUrl).build();
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<ShipmentDetailResponse> createShipment(
            @Valid @RequestBody ShipmentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        var result = validateToken(authHeader, SCOPE_CREATE);
        var shipment = shipmentService.create(request, result.clientId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentDetailResponse.from(shipment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentDetailResponse> getShipment(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        validateToken(authHeader, SCOPE_READ);
        var shipment = shipmentService.get(id);
        if (shipment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ShipmentDetailResponse.from(shipment));
    }

    @GetMapping
    public ResponseEntity<java.util.List<ShipmentDetailResponse>> listShipments(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        validateToken(authHeader, SCOPE_READ);
        var shipments = shipmentService.getAll().stream()
                .map(ShipmentDetailResponse::from)
                .toList();
        return ResponseEntity.ok(shipments);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShipmentDetailResponse> updateShipment(
            @PathVariable String id,
            @Valid @RequestBody ShipmentUpdateRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        validateToken(authHeader, SCOPE_UPDATE);
        var shipment = shipmentService.update(id, request);
        if (shipment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ShipmentDetailResponse.from(shipment));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ShipmentDetailResponse> updateShipmentStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        validateToken(authHeader, SCOPE_UPDATE);
        var shipment = shipmentService.updateStatus(id, request);
        if (shipment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ShipmentDetailResponse.from(shipment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        validateToken(authHeader, SCOPE_DELETE);
        var deleted = shipmentService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private JwtTokenValidator.ValidationResult validateToken(String authHeader, String requiredScope) {
        var token = authHeader.replace("Bearer ", "");

        var jwtResult = jwtTokenValidator.validate(token);
        if (!jwtResult.valid()) {
            throw new TokenValidationException(401, "UNAUTHORIZED",
                    jwtResult.errorCode(), jwtResult.errorMessage());
        }

        var tokenScopes = Set.of(jwtResult.scope().split("\\s+"));
        if (!tokenScopes.contains(requiredScope)) {
            throw new TokenValidationException(403, "FORBIDDEN",
                    "AUTH_INSUFFICIENT_SCOPE",
                    "Token does not have the required scope: " + requiredScope);
        }

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

        return jwtResult;
    }
}
