package com.company.l1api.controller;

import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/shipment")
public class ShipmentController {

    private final L2ClientService l2Client;
    private final CorrelationIdService correlationIdService;

    public ShipmentController(L2ClientService l2Client, CorrelationIdService correlationIdService) {
        this.l2Client = l2Client;
        this.correlationIdService = correlationIdService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map>> createShipment(
            @RequestBody Map<String, Object> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        var correlationId = correlationIdService.generate();
        var token = authHeader.replace("Bearer ", "");
        return l2Client.forwardRequest("/internal/shipment", "POST",
                        token, body, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map>> updateShipment(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        var correlationId = correlationIdService.generate();
        var token = authHeader.replace("Bearer ", "");
        return l2Client.forwardRequest("/internal/shipment/" + id, "PUT",
                        token, body, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }
}
