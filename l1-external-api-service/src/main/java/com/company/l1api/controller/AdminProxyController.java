package com.company.l1api.controller;

import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminProxyController {

    private final L2ClientService l2Client;
    private final CorrelationIdService correlationIdService;

    public AdminProxyController(L2ClientService l2Client, CorrelationIdService correlationIdService) {
        this.l2Client = l2Client;
        this.correlationIdService = correlationIdService;
    }

    @PostMapping("/clients")
    public Mono<ResponseEntity<Map>> registerClient(@RequestBody Map<String, String> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients", "POST",
                null, body, correlationId, Map.class);
    }

    @PostMapping("/clients/{clientId}/scopes")
    public Mono<ResponseEntity<Map>> assignScopes(
            @PathVariable String clientId,
            @RequestBody Map<String, List<String>> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId + "/scopes", "POST",
                null, body, correlationId, Map.class);
    }

    @PostMapping("/clients/{clientId}/unlock")
    public Mono<ResponseEntity<Map>> unlockClient(@PathVariable String clientId) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId + "/unlock", "POST",
                null, null, correlationId, Map.class);
    }

    @GetMapping("/scopes")
    public Mono<ResponseEntity<List>> listScopes() {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/scopes", "GET",
                null, null, correlationId, List.class);
    }

    @PostMapping("/scopes")
    public Mono<ResponseEntity<Map>> createScope(@RequestBody Map<String, String> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/scopes", "POST",
                null, body, correlationId, Map.class);
    }

    @GetMapping("/clients")
    public Mono<ResponseEntity<List>> listClients() {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients", "GET",
                null, null, correlationId, List.class);
    }

    @GetMapping("/clients/{clientId}")
    public Mono<ResponseEntity<Map>> getClient(@PathVariable String clientId) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId, "GET",
                null, null, correlationId, Map.class);
    }

    @PatchMapping("/clients/{clientId}/status")
    public Mono<ResponseEntity<Map>> updateClientStatus(
            @PathVariable String clientId,
            @RequestBody Map<String, String> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId + "/status", "PATCH",
                null, body, correlationId, Map.class);
    }

    @PostMapping("/clients/{clientId}/revoke-tokens")
    public Mono<ResponseEntity<Map>> revokeAllTokens(@PathVariable String clientId) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId + "/revoke-tokens", "POST",
                null, null, correlationId, Map.class);
    }

    @PostMapping("/clients/{clientId}/rotate-secret")
    public Mono<ResponseEntity<Map>> rotateSecret(
            @PathVariable String clientId,
            @RequestBody Map<String, String> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/clients/" + clientId + "/rotate-secret", "POST",
                null, body, correlationId, Map.class);
    }

    @GetMapping("/api-scope-mappings")
    public Mono<ResponseEntity<List>> listApiScopeMappings() {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/api-scope-mappings", "GET",
                null, null, correlationId, List.class);
    }

    @PostMapping("/api-scope-mappings")
    public Mono<ResponseEntity<Map>> createApiScopeMapping(@RequestBody Map<String, String> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardWithStatus("/internal/admin/api-scope-mappings", "POST",
                null, body, correlationId, Map.class);
    }
}
