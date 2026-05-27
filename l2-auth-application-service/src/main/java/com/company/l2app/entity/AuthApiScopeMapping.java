package com.company.l2app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_api_scope_mapping")
public class AuthApiScopeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "http_method", nullable = false, length = 20)
    private String httpMethod;

    @Column(name = "api_pattern", nullable = false, length = 255)
    private String apiPattern;

    @Column(name = "required_scope", nullable = false, length = 100)
    private String requiredScope;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AuthApiScopeMapping() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getApiPattern() { return apiPattern; }
    public void setApiPattern(String apiPattern) { this.apiPattern = apiPattern; }
    public String getRequiredScope() { return requiredScope; }
    public void setRequiredScope(String requiredScope) { this.requiredScope = requiredScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
