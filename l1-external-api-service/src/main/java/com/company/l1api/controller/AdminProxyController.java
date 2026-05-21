package com.company.l1api.controller;

import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.HttpMethod;
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
        return l2Client.forwardRequest("/internal/admin/clients", "POST",
                        null, body, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/clients/{clientId}/scopes")
    public Mono<ResponseEntity<Map>> assignScopes(
            @PathVariable String clientId,
            @RequestBody Map<String, List<String>> body) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardRequest("/internal/admin/clients/" + clientId + "/scopes", "POST",
                        null, body, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/clients/{clientId}/unlock")
    public Mono<ResponseEntity<Map>> unlockClient(@PathVariable String clientId) {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardRequest("/internal/admin/clients/" + clientId + "/unlock", "POST",
                        null, null, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/scopes")
    public Mono<ResponseEntity<List>> listScopes() {
        var correlationId = correlationIdService.generate();
        return l2Client.forwardRequest("/internal/admin/scopes", "GET",
                        null, null, correlationId, List.class)
                .map(ResponseEntity::ok);
    }
}
