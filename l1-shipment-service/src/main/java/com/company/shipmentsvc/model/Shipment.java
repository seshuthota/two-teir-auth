package com.company.shipmentsvc.model;

import com.company.shipmentsvc.dto.ShipmentRequest;
import java.time.Instant;

public class Shipment {

    private final String id;
    private final String clientId;
    private String origin;
    private String destination;
    private String description;
    private String status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Shipment(String id, String clientId, ShipmentRequest req) {
        this.id = id;
        this.clientId = clientId;
        this.origin = req.origin();
        this.destination = req.destination();
        this.description = req.description();
        this.status = "CREATED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getClientId() { return clientId; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
