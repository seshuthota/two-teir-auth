package com.company.l2app.dto;

public record TokenRequest(
        String grantType,
        String clientId,
        String clientSecret
) {}
