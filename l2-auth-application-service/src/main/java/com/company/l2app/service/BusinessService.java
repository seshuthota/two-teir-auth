package com.company.l2app.service;

import org.springframework.stereotype.Service;

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
}
