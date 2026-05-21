package com.company.shipmentsvc.dto;

import jakarta.validation.constraints.NotBlank;

public record ShipmentRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        String description
) {}
