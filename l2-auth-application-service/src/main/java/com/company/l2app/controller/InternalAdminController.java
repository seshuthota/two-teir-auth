package com.company.l2app.controller;

import com.company.l2app.entity.AuthApiScopeMapping;
import com.company.l2app.entity.AuthClient;
import com.company.l2app.entity.AuthClientScope;
import com.company.l2app.entity.AuthScope;
import com.company.l2app.redis.TokenRedisRepository;
import com.company.l2app.repository.AuthApiScopeMappingRepository;
import com.company.l2app.repository.AuthClientRepository;
import com.company.l2app.repository.AuthClientScopeRepository;
import com.company.l2app.repository.AuthScopeRepository;
import com.company.l2app.security.ClientSecretHasher;
import com.company.l2app.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
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
    private final AuthApiScopeMappingRepository apiScopeMappingRepository;
    private final ClientSecretHasher secretHasher;
    private final TokenRedisRepository tokenRedisRepository;
    private final AuditService auditService;
    private final long accessTokenTtlSeconds;

    public InternalAdminController(AuthClientRepository clientRepository,
                                    AuthClientScopeRepository clientScopeRepository,
                                    AuthScopeRepository scopeRepository,
                                    AuthApiScopeMappingRepository apiScopeMappingRepository,
                                    ClientSecretHasher secretHasher,
                                    TokenRedisRepository tokenRedisRepository,
                                    AuditService auditService,
                                    @Value("${auth.jwt.access-token-expiration}") long accessTokenTtlSeconds) {
        this.clientRepository = clientRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.scopeRepository = scopeRepository;
        this.apiScopeMappingRepository = apiScopeMappingRepository;
        this.secretHasher = secretHasher;
        this.tokenRedisRepository = tokenRedisRepository;
        this.auditService = auditService;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
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

    @PostMapping("/scopes")
    public ResponseEntity<Map<String, Object>> createScope(@RequestBody Map<String, String> body) {
        var scopeName = body.get("scopeName");
        var description = body.get("description");

        if (scopeName == null || scopeName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "scopeName is required"));
        }

        var scope = new AuthScope(scopeName, description != null ? description : "");
        scopeRepository.save(scope);
        return ResponseEntity.status(201).body(Map.of(
                "scopeName", scopeName,
                "description", description,
                "message", "Scope created successfully"));
    }

    @GetMapping("/clients")
    public ResponseEntity<List<AuthClient>> listClients() {
        return ResponseEntity.ok(clientRepository.findAll());
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<?> getClient(@PathVariable String clientId) {
        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404, "error", "NOT_FOUND",
                    "message", "Client not found: " + clientId));
        }
        return ResponseEntity.ok(clientOpt.get());
    }

    @PatchMapping("/clients/{clientId}/status")
    public ResponseEntity<Map<String, Object>> updateClientStatus(
            @PathVariable String clientId,
            @RequestBody Map<String, String> body) {
        var newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "status is required"));
        }

        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404, "error", "NOT_FOUND",
                    "message", "Client not found: " + clientId));
        }

        var client = clientOpt.get();
        client.setStatus(newStatus);
        clientRepository.save(client);
        auditService.log(clientId, null, "STATUS_CHANGED_TO_" + newStatus, "SUCCESS");

        return ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "status", newStatus,
                "message", "Client status updated"));
    }

    @PostMapping("/clients/{clientId}/revoke-tokens")
    public ResponseEntity<Map<String, Object>> revokeAllTokens(@PathVariable String clientId) {
        var sessions = tokenRedisRepository.getActiveClientSessions(clientId);
        for (var jti : sessions) {
            tokenRedisRepository.revokeToken(jti, accessTokenTtlSeconds);
            tokenRedisRepository.deleteAccessTokenMetadata(jti);
            tokenRedisRepository.removeFromClientSessions(clientId, jti);
        }
        auditService.log(clientId, null, "ALL_TOKENS_REVOKED", "SUCCESS", sessions.size() + " tokens revoked");

        return ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "revokedCount", sessions.size(),
                "message", "All active tokens revoked"));
    }

    @PostMapping("/clients/{clientId}/rotate-secret")
    public ResponseEntity<Map<String, Object>> rotateSecret(
            @PathVariable String clientId,
            @RequestBody Map<String, String> body) {
        var newSecret = body.get("newSecret");
        if (newSecret == null || newSecret.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "newSecret is required"));
        }

        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404, "error", "NOT_FOUND",
                    "message", "Client not found: " + clientId));
        }

        var client = clientOpt.get();
        client.setClientSecretHash(secretHasher.hash(newSecret));
        clientRepository.save(client);
        auditService.log(clientId, null, "SECRET_ROTATED", "SUCCESS");

        return ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "message", "Secret rotated successfully"));
    }

    @GetMapping("/api-scope-mappings")
    public ResponseEntity<List<AuthApiScopeMapping>> listApiScopeMappings() {
        return ResponseEntity.ok(apiScopeMappingRepository.findAll());
    }

    @PostMapping("/api-scope-mappings")
    public ResponseEntity<Map<String, Object>> createApiScopeMapping(
            @RequestBody Map<String, String> body) {
        var httpMethod = body.get("httpMethod");
        var apiPattern = body.get("apiPattern");
        var requiredScope = body.get("requiredScope");

        if (httpMethod == null || apiPattern == null || requiredScope == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400, "error", "BAD_REQUEST",
                    "message", "httpMethod, apiPattern, and requiredScope are required"));
        }

        var mapping = new AuthApiScopeMapping();
        mapping.setHttpMethod(httpMethod);
        mapping.setApiPattern(apiPattern);
        mapping.setRequiredScope(requiredScope);
        mapping.setStatus("ACTIVE");
        apiScopeMappingRepository.save(mapping);

        return ResponseEntity.status(201).body(Map.of(
                "httpMethod", httpMethod,
                "apiPattern", apiPattern,
                "requiredScope", requiredScope,
                "status", "ACTIVE",
                "message", "API-scope mapping created"));
    }
}
