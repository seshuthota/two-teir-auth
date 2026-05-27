package com.company.shipmentsvc.dto;

import com.company.shipmentsvc.model.Shipment;
import java.time.Instant;

public record ShipmentDetailResponse(
        String id,
        String clientId,
        String origin,
        String destination,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShipmentDetailResponse from(Shipment s) {
        return new ShipmentDetailResponse(
                s.getId(), s.getClientId(),
                s.getOrigin(), s.getDestination(),
                s.getDescription(), s.getStatus(),
                s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
