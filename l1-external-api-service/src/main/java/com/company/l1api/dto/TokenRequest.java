package com.company.l1api.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank String grantType,
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {}
