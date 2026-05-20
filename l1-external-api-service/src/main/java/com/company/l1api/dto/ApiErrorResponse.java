package com.company.l1api.dto;

public record ApiErrorResponse(
        int status,
        String error,
        String code,
        String message
) {}
