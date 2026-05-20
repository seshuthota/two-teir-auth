package com.company.l2app.repository;

import com.company.l2app.entity.AuthClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthClientRepository extends JpaRepository<AuthClient, Long> {
    Optional<AuthClient> findByClientId(String clientId);
}
