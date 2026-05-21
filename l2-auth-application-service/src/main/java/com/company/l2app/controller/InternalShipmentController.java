package com.company.l2app.controller;

import com.company.l2app.service.BusinessService;
import com.company.l2app.service.PermissionService;
import com.company.l2app.security.TokenValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/shipment")
public class InternalShipmentController {

    private final TokenValidator tokenValidator;
    private final PermissionService permissionService;
    private final BusinessService businessService;

    public InternalShipmentController(TokenValidator tokenValidator,
                                       PermissionService permissionService,
                                       BusinessService businessService) {
        this.tokenValidator = tokenValidator;
        this.permissionService = permissionService;
        this.businessService = businessService;
    }

    @PostMapping
    public ResponseEntity<?> createShipment(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {
        var token = authHeader.replace("Bearer ", "");
        var result = tokenValidator.validate(token);
        if (!result.valid()) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "UNAUTHORIZED",
                    "code", result.errorCode(), "message", result.errorMessage()
            ));
        }

        if (!permissionService.hasRequiredScope("POST", "/shipment", result.scope())) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", 403, "error", "FORBIDDEN",
                    "code", "AUTH_INSUFFICIENT_SCOPE",
                    "message", "Token does not have the required scope"
            ));
        }

        return ResponseEntity.ok(businessService.createShipment(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateShipment(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {
        var token = authHeader.replace("Bearer ", "");
        var result = tokenValidator.validate(token);
        if (!result.valid()) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401, "error", "UNAUTHORIZED",
                    "code", result.errorCode(), "message", result.errorMessage()
            ));
        }

        if (!permissionService.hasRequiredScope("PUT", "/shipment/{id}", result.scope())) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", 403, "error", "FORBIDDEN",
                    "code", "AUTH_INSUFFICIENT_SCOPE",
                    "message", "Token does not have the required scope"
            ));
        }

        return ResponseEntity.ok(businessService.updateShipment(id, body));
    }
}
