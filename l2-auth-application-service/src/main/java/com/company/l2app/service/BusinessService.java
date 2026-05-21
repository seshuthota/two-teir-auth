package com.company.l2app.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class BusinessService {

    public Map<String, Object> getTracking(String awb) {
        return Map.of(
                "awb", awb,
                "status", "IN_TRANSIT",
                "origin", "Warehouse A",
                "destination", "Distribution Center B",
                "estimatedDelivery", "2026-05-22"
        );
    }

    public Map<String, Object> createShipment(Map<String, Object> request) {
        return Map.of(
                "shipmentId", "SHP-" + System.currentTimeMillis(),
                "status", "CREATED",
                "details", request
        );
    }

    public Map<String, Object> updateStatus(Map<String, Object> request) {
        return Map.of(
                "status", "UPDATED",
                "previousStatus", request.getOrDefault("currentStatus", "UNKNOWN"),
                "newStatus", request.get("newStatus")
        );
    }

    public Map<String, Object> updateShipment(String shipmentId, Map<String, Object> request) {
        return Map.of(
                "shipmentId", shipmentId,
                "status", request.getOrDefault("status", "UPDATED"),
                "location", request.getOrDefault("location", "Unknown"),
                "updatedAt", Instant.now().toString()
        );
    }

    public Map<String, Object> getInvoice(String invoiceId) {
        return Map.of(
                "invoiceId", invoiceId,
                "amount", "1250.00",
                "currency", "USD",
                "status", "PAID",
                "issuedAt", "2026-05-01",
                "dueDate", "2026-05-15"
        );
    }
}
