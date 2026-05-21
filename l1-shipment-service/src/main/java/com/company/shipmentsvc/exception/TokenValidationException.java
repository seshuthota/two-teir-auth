package com.company.shipmentsvc.exception;

public class TokenValidationException extends RuntimeException {

    private final int httpStatus;
    private final String error;
    private final String code;

    public TokenValidationException(int httpStatus, String error, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.error = error;
        this.code = code;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getError() { return error; }
    public String getCode() { return code; }
}
