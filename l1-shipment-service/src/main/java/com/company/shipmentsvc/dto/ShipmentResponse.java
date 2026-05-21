package com.company.shipmentsvc.dto;

public record ShipmentResponse(
        String shipmentId,
        String status,
        String origin,
        String destination,
        String description
) {}
