package com.company.l2app.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_client_scope",
       uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "scope_name"}))
public class AuthClientScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "scope_name", nullable = false, length = 100)
    private String scopeName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuthClientScope() {}

    public AuthClientScope(String clientId, String scopeName) {
        this.clientId = clientId;
        this.scopeName = scopeName;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getScopeName() { return scopeName; }
    public void setScopeName(String scopeName) { this.scopeName = scopeName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
