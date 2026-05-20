package com.company.l2app.service;

import com.company.l2app.entity.TokenAudit;
import com.company.l2app.repository.TokenAuditRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final TokenAuditRepository auditRepository;

    public AuditService(TokenAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String clientId, String tokenId, String eventType, String status) {
        var audit = new TokenAudit(clientId, tokenId, eventType, status);
        auditRepository.save(audit);
    }

    public void log(String clientId, String tokenId, String eventType, String status,
                     String failureReason) {
        var audit = new TokenAudit(clientId, tokenId, eventType, status);
        audit.setFailureReason(failureReason);
        auditRepository.save(audit);
    }
}
