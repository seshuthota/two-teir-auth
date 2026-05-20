package com.company.l2app.repository;

import com.company.l2app.entity.TokenAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenAuditRepository extends JpaRepository<TokenAudit, Long> {
}
