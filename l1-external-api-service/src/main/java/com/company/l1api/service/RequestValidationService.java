package com.company.l1api.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestValidationService {

    private static final int MAX_PAYLOAD_SIZE = 1024 * 100;

    public void validateContentType(String contentType) {
        if (contentType == null || !contentType.startsWith("application/json")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json");
        }
    }

    public void validatePayloadSize(long contentLength) {
        if (contentLength > MAX_PAYLOAD_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request payload too large");
        }
    }
}
