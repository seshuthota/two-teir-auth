package com.company.l1api.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CorrelationIdService {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
