package com.company.l1api.controller;

import com.company.l1api.service.CorrelationIdService;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/invoice")
public class InvoiceController {

    private final L2ClientService l2Client;
    private final CorrelationIdService correlationIdService;

    public InvoiceController(L2ClientService l2Client, CorrelationIdService correlationIdService) {
        this.l2Client = l2Client;
        this.correlationIdService = correlationIdService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map>> getInvoice(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        var correlationId = correlationIdService.generate();
        var token = authHeader.replace("Bearer ", "");
        return l2Client.forwardRequest("/internal/invoice/" + id, "GET",
                        token, null, correlationId, Map.class)
                .map(ResponseEntity::ok);
    }
}
