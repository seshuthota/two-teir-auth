package com.company.l2app.controller;

import com.company.l2app.service.BusinessService;
import com.company.l2app.service.PermissionService;
import com.company.l2app.security.TokenValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/status")
public class InternalStatusController {

    private final TokenValidator tokenValidator;
    private final PermissionService permissionService;
    private final BusinessService businessService;

    public InternalStatusController(TokenValidator tokenValidator,
                                     PermissionService permissionService,
                                     BusinessService businessService) {
        this.tokenValidator = tokenValidator;
        this.permissionService = permissionService;
        this.businessService = businessService;
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateStatus(
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

        if (!permissionService.hasRequiredScope("POST", "/status/update", result.scope())) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", 403, "error", "FORBIDDEN",
                    "code", "AUTH_INSUFFICIENT_SCOPE",
                    "message", "Token does not have the required scope"
            ));
        }

        return ResponseEntity.ok(businessService.updateStatus(body));
    }
}
