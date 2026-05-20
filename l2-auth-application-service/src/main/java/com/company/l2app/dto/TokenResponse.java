package com.company.l2app.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresIn,
        String scope
) {}
