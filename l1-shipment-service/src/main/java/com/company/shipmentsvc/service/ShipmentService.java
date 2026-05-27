package com.company.shipmentsvc.service;

import com.company.shipmentsvc.dto.ShipmentRequest;
import com.company.shipmentsvc.dto.ShipmentUpdateRequest;
import com.company.shipmentsvc.dto.StatusUpdateRequest;
import com.company.shipmentsvc.model.Shipment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShipmentService {

    private final Map<String, Shipment> store = new ConcurrentHashMap<>();

    public Shipment create(ShipmentRequest req, String clientId) {
        var id = "SHP-" + System.currentTimeMillis();
        var shipment = new Shipment(id, clientId, req);
        store.put(id, shipment);
        return shipment;
    }

    public Shipment get(String id) {
        return store.get(id);
    }

    public Collection<Shipment> getAll() {
        return store.values();
    }

    public Shipment update(String id, ShipmentUpdateRequest req) {
        var shipment = store.get(id);
        if (shipment == null) return null;
        shipment.setOrigin(req.origin());
        shipment.setDestination(req.destination());
        shipment.setDescription(req.description());
        shipment.setUpdatedAt(Instant.now());
        return shipment;
    }

    public Shipment updateStatus(String id, StatusUpdateRequest req) {
        var shipment = store.get(id);
        if (shipment == null) return null;
        shipment.setStatus(req.status());
        shipment.setUpdatedAt(Instant.now());
        return shipment;
    }

    public boolean delete(String id) {
        return store.remove(id) != null;
    }
}
