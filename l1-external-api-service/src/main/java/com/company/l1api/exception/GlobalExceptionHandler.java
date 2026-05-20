package com.company.l1api.exception;

import com.company.l1api.dto.ApiErrorResponse;
import com.company.l1api.service.L2ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(L2ClientService.L2ClientException.class)
    public ResponseEntity<ApiErrorResponse> handleL2Error(L2ClientService.L2ClientException ex) {
        return ResponseEntity.status(ex.getErrorResponse().status()).body(ex.getErrorResponse());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(500, "INTERNAL_ERROR", "SERVICE_ERROR",
                        "An unexpected error occurred"));
    }
}
