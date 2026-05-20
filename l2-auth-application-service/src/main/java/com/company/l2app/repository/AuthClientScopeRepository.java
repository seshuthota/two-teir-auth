package com.company.l2app.repository;

import com.company.l2app.entity.AuthClientScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthClientScopeRepository extends JpaRepository<AuthClientScope, Long> {
    List<AuthClientScope> findByClientId(String clientId);
}
