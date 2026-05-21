package com.company.l2app.controller;

import com.company.l2app.entity.AuthClient;
import com.company.l2app.entity.AuthClientScope;
import com.company.l2app.entity.AuthScope;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.repository.AuthClientRepository;
import com.company.l2app.repository.AuthClientScopeRepository;
import com.company.l2app.repository.AuthScopeRepository;
import com.company.l2app.security.ClientSecretHasher;
import com.company.l2app.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/admin")
public class InternalAdminController {

    private final AuthClientRepository clientRepository;
    private final AuthClientScopeRepository clientScopeRepository;
    private final AuthScopeRepository scopeRepository;
    private final ClientSecretHasher secretHasher;
    private final TokenRedisRepository tokenRedisRepository;
    private final AuditService auditService;

    public InternalAdminController(AuthClientRepository clientRepository,
                                    AuthClientScopeRepository clientScopeRepository,
                                    AuthScopeRepository scopeRepository,
                                    ClientSecretHasher secretHasher,
                                    TokenRedisRepository tokenRedisRepository,
                                    AuditService auditService) {
        this.clientRepository = clientRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.scopeRepository = scopeRepository;
        this.secretHasher = secretHasher;
        this.tokenRedisRepository = tokenRedisRepository;
        this.auditService = auditService;
    }

    @PostMapping("/clients")
    public ResponseEntity<Map<String, Object>> registerClient(@RequestBody Map<String, String> body) {
        var clientId = body.get("clientId");
        var clientName = body.get("clientName");
        var clientSecret = body.get("clientSecret");

        if (clientId == null || clientName == null || clientSecret == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "clientId, clientName, and clientSecret are required"));
        }

        if (clientRepository.findByClientId(clientId).isPresent()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", 409, "error", "CONFLICT",
                    "message", "Client already exists: " + clientId));
        }

        var hash = secretHasher.hash(clientSecret);
        var client = new AuthClient(clientId, clientName, hash, "ACTIVE");
        clientRepository.save(client);
        auditService.log(clientId, null, "CLIENT_REGISTERED", "SUCCESS");

        return ResponseEntity.status(201).body(Map.of(
                "clientId", clientId,
                "clientName", clientName,
                "status", "ACTIVE",
                "message", "Client registered successfully"));
    }

    @PostMapping("/clients/{clientId}/scopes")
    public ResponseEntity<Map<String, Object>> assignScopes(
            @PathVariable String clientId,
            @RequestBody Map<String, List<String>> body) {
        var scopes = body.get("scopes");
        if (scopes == null || scopes.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "scopes list is required"));
        }

        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404, "error", "NOT_FOUND",
                    "message", "Client not found: " + clientId));
        }

        for (var scopeName : scopes) {
            var existing = clientScopeRepository.findByClientId(clientId);
            var alreadyAssigned = existing.stream()
                    .anyMatch(s -> s.getScopeName().equals(scopeName));
            if (!alreadyAssigned) {
                clientScopeRepository.save(new AuthClientScope(clientId, scopeName));
            }
        }

        auditService.log(clientId, null, "SCOPES_ASSIGNED", "SUCCESS");
        return ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "scopes", scopes,
                "message", "Scopes assigned successfully"));
    }

    @PostMapping("/clients/{clientId}/unlock")
    public ResponseEntity<Map<String, Object>> unlockClient(@PathVariable String clientId) {
        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404, "error", "NOT_FOUND",
                    "message", "Client not found: " + clientId));
        }

        tokenRedisRepository.resetFailedAuth(clientId);
        var client = clientOpt.get();
        client.setStatus("ACTIVE");
        clientRepository.save(client);
        auditService.log(clientId, null, "CLIENT_UNLOCKED", "SUCCESS");

        return ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "status", "ACTIVE",
                "message", "Client unlocked successfully"));
    }

    @GetMapping("/scopes")
    public ResponseEntity<List<AuthScope>> listScopes() {
        return ResponseEntity.ok(scopeRepository.findAll());
    }
}
