package com.company.l1api.controller;

import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final L2ClientService l2Client;
    private final CorrelationIdService correlationIdService;

    public TrackingController(L2ClientService l2Client, CorrelationIdService correlationIdService) {
        this.l2Client = l2Client;
        this.correlationIdService = correlationIdService;
    }

    @GetMapping("/{awb}")
    public Mono<ResponseEntity<Map>> getTracking(
            @PathVariable String awb,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        var correlationId = correlationIdService.generate();
        var token = authHeader.replace("Bearer ", "");
        return l2Client.forwardRequest("/internal/tracking/" + awb, "GET",
                        token, null, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }
}
