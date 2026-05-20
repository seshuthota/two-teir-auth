package com.company.l2app.repository;

import com.company.l2app.entity.AuthApiScopeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthApiScopeMappingRepository extends JpaRepository<AuthApiScopeMapping, Long> {
    Optional<AuthApiScopeMapping> findByHttpMethodAndApiPatternAndStatus(
            String httpMethod, String apiPattern, String status);
}
